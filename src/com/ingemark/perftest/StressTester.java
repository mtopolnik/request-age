package com.ingemark.perftest;

import static com.ingemark.perftest.Message.DIVISOR;
import static com.ingemark.perftest.Message.ERROR;
import static com.ingemark.perftest.Message.INIT;
import static com.ingemark.perftest.Message.INTENSITY;
import static com.ingemark.perftest.Message.SHUTDOWN;
import static com.ingemark.perftest.Message.STATS;
import static com.ingemark.perftest.StressTestServer.NETTY_PORT;
import static com.ingemark.perftest.Util.join;
import static com.ingemark.perftest.Util.nettySend;
import static com.ingemark.perftest.Util.now;
import static com.ingemark.perftest.plugin.StressTestActivator.stressTestPlugin;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.core.runtime.FileLocator.getBundleFile;
import static org.eclipse.jdt.launching.JavaRuntime.getDefaultVMInstall;
import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.channel.Channels.pipelineFactory;
import static org.jboss.netty.handler.codec.serialization.ClassResolvers.softCachingResolver;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;

import org.eclipse.jdt.launching.IVMInstall;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import org.slf4j.Logger;

import com.ingemark.perftest.script.Parser;
import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Response;

public class StressTester implements Runnable
{
  static final Logger log = getLogger(StressTester.class);
  public static final int TIMESLOTS_PER_SEC = 20, HIST_SIZE = 200;
  final ScheduledExecutorService sched = newScheduledThreadPool(2, new ThreadFactory(){
    volatile int i; public Thread newThread(Runnable r) {
      return new Thread(r, "StressTester scheduler #"+i++);
  }});
  final Script script;
  final Random rnd = new Random();
  final ClientBootstrap netty;
  final Channel channel;
  final AsyncHttpClient client;
  volatile int intensity = 1, updateDivisor = 1;
  volatile ScheduledFuture<?> nettyReportingTask;

  public StressTester(Script script) {
    final AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
    b.setProxyServer(toProxyServer(script.initEnv.get("system.proxy")));
    this.client = new AsyncHttpClient();
    this.script = script;
    this.netty = netty();
    log.debug("Connecting to server");
    this.channel = channel(netty);
    log.debug("Connected");
  }

  Channel channel(ClientBootstrap netty) {
    try {
      return netty.connect(new InetSocketAddress("localhost", NETTY_PORT)).await().getChannel();
    } catch (InterruptedException e) {return null;}
  }

  ClientBootstrap netty() {
    log.debug("Starting Client Netty");
    final ClientBootstrap b = new ClientBootstrap(
        new NioClientSocketChannelFactory(newCachedThreadPool(),newCachedThreadPool()));
    b.setPipelineFactory(pipelineFactory(pipeline(
      new ObjectDecoder(softCachingResolver(getClass().getClassLoader())),
      new SimpleChannelHandler() {
        @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
          try {
            final Message msg = (Message)e.getMessage();
            switch (msg.type) {
            case DIVISOR: updateDivisor = (Integer) msg.value; break;
            case INTENSITY: intensity = (Integer) msg.value; break;
            case SHUTDOWN:
              sched.schedule(new Runnable() { public void run() {shutdown();} }, 0, SECONDS);
              break;
            }
          } catch (Throwable t) {t.printStackTrace();}
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
          log.error("Netty error", e);
        }
      }
      , new ObjectEncoder()
      )));
    return b;
  }

  public void runTest() throws Exception {
    nettySend(channel, new Message(INIT, script.getInit()), true);
    try {
      warmup();
      sched.scheduleAtFixedRate(new Runnable() {
        public void run() {
          final List<Stats> stats = stats();
          if (stats.isEmpty()) return;
          nettySend(channel, new Message(STATS, stats.toArray(new Stats[stats.size()])));
        }}, 100, 1_000_000/TIMESLOTS_PER_SEC, MICROSECONDS);
      run();
    }
    catch (Throwable t) {
      nettySend(channel, new Message(ERROR, t));
      shutdown();
    }
  }

  void warmup() throws Exception {
    log.debug("Warming up");
    final Script.Instance si = script.warmupInstance();
    for (RequestProvider rp; (rp = si.nextRequestProvider()) != null;) {
      try {
        final Response r = client.executeRequest(rp.request(client)).get();
        if (!si.result(r))
          throw new RuntimeException("Stage " + rp.name + " failed with response: " + r);
      } catch (ExecutionException e) {
        throw new RuntimeException(
            "Stage " + rp.name + " failed to send request: " +
                e.getCause().getClass().getSimpleName() + " " +
                e.getCause().getMessage());
      }
    }
    log.debug("Warmup done");
  }

  @Override public void run() {
    final long start = now();
    final Script.Instance si = script.testInstance();
    try {
      new AsyncCompletionHandlerBase() {
        volatile RequestProvider rp;
        volatile int startSlot;
        void newRequest() throws IOException {
          rp = si.nextRequestProvider();
          if (rp == null) return;
          startSlot = rp.liveStats.registerReq();
          client.executeRequest(rp.request(client), this);
        }
        {newRequest();}
        @Override public Response onCompleted(Response resp) throws IOException {
          final boolean succ = si.result(resp);
          rp.liveStats.deregisterReq(startSlot, succ);
          newRequest();
          return resp;
        }
        @Override public void onThrowable(Throwable t) {
          rp.liveStats.deregisterReq(startSlot, false);
          si.result(null);
        }
      };
      final long delay = 1000_000_000L/intensity - (now()-start);
      if (!sched.isShutdown()) sched.schedule(this, delay, NANOSECONDS);
    } catch (Throwable t) {
      log.error("Error in request firing task", t);
    }
  }

  List<Stats> stats() {
    final List<Stats> ret = new ArrayList<>(script.testReqs.size());
    for (RequestProvider p : script.testReqs) {
      final Stats stats = p.liveStats.stats(updateDivisor);
      if (stats != null) ret.add(stats);
    }
    return ret;
  }

  public void shutdown() {
    try {
      sched.shutdown();
      sched.awaitTermination(5, SECONDS);
      client.close();
      log.debug("HTTP Client shut down");
      netty.shutdown();
      log.debug("Netty client shut down");
    } catch (InterruptedException e) { }
  }

  static final String[]
    javaCandidates = {"javaw", "javaw.exe", "java", "java.exe", "j9w", "j9w.exe", "j9", "j9.exe"},
    javaDirCandidates = {"bin", "jre" + File.separator + "bin"};

  static String java() {
    final IVMInstall inst = getDefaultVMInstall();
    if (inst == null) return "java";
    final File loc = inst.getInstallLocation();
    if (loc == null) return "java";
    for (String java : javaCandidates) {
      for (String javaDir : javaDirCandidates) {
        final File javaFile = new File(loc, javaDir + File.separator + java);
        if (javaFile.isFile()) return javaFile.getAbsolutePath();
      }
    }
    return "java";
  }
  public static Process launchTester(String scriptFile) {
    try {
      final String bpath = getBundleFile(stressTestPlugin().bundle()).getAbsolutePath();
      final String slash = File.separator;
      final String cp = join(File.pathSeparator, bpath, bpath+slash+"bin", bpath+slash+"lib");
      log.debug("Launching {} with classpath {}", StressTester.class.getSimpleName(), cp);
      return new ProcessBuilder(java(), "-Xmx128m", "-XX:+UseConcMarkSweepGC", "-cp", cp,
          StressTester.class.getName(), scriptFile).inheritIO().start();
    } catch (IOException e) { throw new RuntimeException(e); }
  }

  static ProxyServer toProxyServer(String proxyString) {
    if (proxyString == null) return null;
    final String[] parts = proxyString.split(":");
    return new ProxyServer(parts[0], parts.length > 1? Integer.valueOf(parts[1]) : 80);
  }
  public static void main(String[] args) {
    try {
      log.info("Loading script {}", args[0]);
      new StressTester(new Parser(new FileInputStream(args[0])).parse()).runTest();
    } catch (Throwable t) { log.error("", t); }
  }
}
