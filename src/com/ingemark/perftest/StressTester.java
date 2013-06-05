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
import static com.ingemark.perftest.Util.toProxyServer;
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
import static org.mozilla.javascript.Context.javaToJS;
import static org.mozilla.javascript.ScriptableObject.getTypedProperty;
import static org.mozilla.javascript.ScriptableObject.putProperty;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;

import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Request;
import com.ning.http.client.Response;

public class StressTester implements Runnable
{
  static final Logger log = getLogger(StressTester.class);
  public static final int TIMESLOTS_PER_SEC = 20, HIST_SIZE = 200;
  static final ContextFactory fac = ContextFactory.getGlobal();
  final ScheduledExecutorService sched = newScheduledThreadPool(2, new ThreadFactory(){
    volatile int i; public Thread newThread(Runnable r) {
      return new Thread(r, "StressTester scheduler #"+i++);
  }});
  private final ScriptableObject jsScope;
  private final ClientBootstrap netty;
  private final Channel channel;
  private final AsyncHttpClient client;
  private volatile int intensity, updateDivisor = 1;
  private final Map<String, LiveStats> lsmap = new HashMap<>();
  private ScheduledFuture<?> testTask;

  public StressTester(String fname) {
    this.jsScope = initJsScope(fname);
    final AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
    jsCall("configure", b);
    this.client = new AsyncHttpClient(b.build());
    this.netty = netty();
    log.debug("Connecting to server");
    this.channel = channel(netty);
    log.debug("Connected");
  }

  ScriptableObject initJsScope(final String fname) {
    return (ScriptableObject) fac.call(new ContextAction() {
      public Object run(Context cx) {
        try {
          final Reader js = new FileReader(fname);
          final ScriptableObject scope = cx.initStandardObjects(null, true);
          cx.evaluateString(scope,
              "RegExp; getClass; java; Packages; JavaAdapter;", "lazyLoad", 0, null);
          cx.evaluateReader(scope, js, "<here>", 1, null);
        }
        catch (IOException e) { throw new RuntimeException(e); }
        putProperty(jsScope, "$", javaToJS(new JsHelper(), jsScope));
        return jsScope;
      }
    });
  }

  class JsHelper {
    int index;
    public void proxy(AsyncHttpClientConfig.Builder b, String proxyStr) {
      b.setProxyServer(toProxyServer(proxyStr));
    }
    public Response initHttp(String reqName, String method, String url, String body)
    throws Exception
    {
      final BoundRequestBuilder b = client.prepareConnect(url).setMethod(method);
      final Request request = (body != null? b.setBody(body) : b).build();
      lsmap.put(reqName, new LiveStats(index++, reqName));
      return client.executeRequest(request).get();
    }
    public boolean http(String reqName, String method, String url, String body, final Function f) {
      final BoundRequestBuilder b = client.prepareConnect(url).setMethod(method);
      final Request request = (body != null? b.setBody(body) : b).build();
      final LiveStats liveStats = lsmap.get(reqName);
      final int startSlot = liveStats.registerReq();
      try {
        client.executeRequest(request, new AsyncCompletionHandlerBase() {
          @Override public Response onCompleted(final Response resp) throws IOException {
            fac.call(new ContextAction() {
              @Override public Object run(Context cx) {
                final Object res = f.call(cx, jsScope, null, new Object[] {resp});
                liveStats.deregisterReq(startSlot, res != null && !res.equals(Boolean.FALSE));
                return null;
              }
            });
            return resp;
          }
          @Override public void onThrowable(Throwable t) {
            liveStats.deregisterReq(startSlot, false);
          }
        });
        return true;
      } catch (IOException e) { throw new RuntimeException(e); }
    }
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
            case INTENSITY:
              scheduleTest((Integer) msg.value);
              break;
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

  void runTest() throws Exception {
    warmup();
    nettySend(channel, new Message(INIT, collectIndices()), true);
    try {
      scheduleTest(1);
      sched.scheduleAtFixedRate(new Runnable() { public void run() {
        final List<Stats> stats = stats();
        if (stats.isEmpty()) return;
        nettySend(channel, new Message(STATS, stats.toArray(new Stats[stats.size()])));
      }}, 100, SECONDS.toMicros(1)/TIMESLOTS_PER_SEC, MICROSECONDS);

    }
    catch (Throwable t) {
      nettySend(channel, new Message(ERROR, t));
      shutdown();
    }
  }

  void warmup() throws Exception {
    log.debug("Warming up");
    jsCall("init");
    jsScope.sealObject();
    log.debug("Warmup done");
  }

  synchronized void scheduleTest(int newIntensity) {
    if (intensity == newIntensity) return;
    intensity = newIntensity;
    if (testTask != null) testTask.cancel(false);
    try { testTask.get(); }
    catch (InterruptedException | ExecutionException e) { throw new RuntimeException(e); }
    testTask = intensity > 0?
      sched.scheduleAtFixedRate(this, 0, SECONDS.toNanos(1)/intensity, NANOSECONDS)
      : null;
  }

  @Override public void run() { jsCall("test"); }

  ArrayList<Integer> collectIndices() {
    final ArrayList<Integer> ret = new ArrayList<>();
    for (LiveStats ls : lsmap.values()) if (ls.name != null) ret.add(ls.index);
    return ret;
  }

  Object jsCall(final String fn, final Object... args) {
    return fac.call(new ContextAction() { @Override public Object run(Context cx) {
      return getTypedProperty(jsScope, fn, Function.class).call(cx, jsScope, null, args);
    }});
  }

  LiveStats livestats(String name) {
    final LiveStats liveStats = lsmap.get(name);
    return liveStats != null? liveStats : new LiveStats(0, name) {
      @Override synchronized int registerReq() {
        return super.registerReq();
      }
    };
  }

  List<Stats> stats() {
    final List<Stats> ret = new ArrayList<>(lsmap.size());
    for (LiveStats ls : lsmap.values()) {
      final Stats stats = ls.stats(updateDivisor);
      if (stats != null) ret.add(stats);
    }
    return ret;
  }

  void shutdown() {
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

  public static void main(String[] args) {
    args[0] = "src/test.js";
    try {
      log.info("Loading script {}", args[0]);
      new StressTester(args[0]).runTest();
    } catch (Throwable t) { log.error("", t); }
  }
}
