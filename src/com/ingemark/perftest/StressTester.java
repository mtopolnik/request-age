package com.ingemark.perftest;

import static com.ingemark.perftest.Message.DIVISOR;
import static com.ingemark.perftest.Message.INIT;
import static com.ingemark.perftest.Message.INTENSITY;
import static com.ingemark.perftest.Message.SHUTDOWN;
import static com.ingemark.perftest.Util.now;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.channel.Channels.pipelineFactory;
import static org.jboss.netty.handler.codec.serialization.ClassResolvers.cacheDisabled;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;

import com.ingemark.perftest.script.Parser;
import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Response;

public class StressTester implements Runnable
{
  public static final int TIMESLOTS_PER_SEC = 20, HIST_SIZE = 200;
  final ScheduledExecutorService sched = Executors.newScheduledThreadPool(2);
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
    this.channel = channel(netty);
  }

  Channel channel(ClientBootstrap netty) {
    try {
      return netty.connect(new InetSocketAddress("localhost", 49131)).await().getChannel();
    } catch (InterruptedException e) {return null;}
  }

  ClientBootstrap netty() {
    final ClientBootstrap b = new ClientBootstrap(
        new NioServerSocketChannelFactory(newCachedThreadPool(),newCachedThreadPool()));
    b.setPipelineFactory(pipelineFactory(pipeline(
      new ObjectDecoder(cacheDisabled(getClass().getClassLoader())),
      new SimpleChannelHandler() {
        @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
          try {
            final Message msg = (Message)e.getMessage();
            System.out.println("StressTester received " + msg);
            switch (msg.type) {
            case DIVISOR: updateDivisor = (int) msg.value; break;
            case INTENSITY: intensity = (int) msg.value; break;
            case SHUTDOWN:
              sched.schedule(new Runnable() { public void run() {shutdown();} }, 0, SECONDS);
              break;
            }
          } catch (Throwable t) {t.printStackTrace();}
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
          System.out.println("StressTester netty error " + e.getCause().getMessage());
        }
      },
      new ObjectEncoder())));
    return b;
  }

  public void setIntensity(int intensity) { this.intensity = intensity; }

  public void runTest() throws Exception {
    channel.write(new Message(INIT, script.testReqs.size())).await();
    warmup();
    run();
    sched.scheduleAtFixedRate(new Runnable() {
      public void run() {
        final List<Stats> stats = stats();
        if (stats.isEmpty()) return;
        channel.write(stats.toArray(new Stats[stats.size()]));
    }}, 100, 1_000_000/TIMESLOTS_PER_SEC, MICROSECONDS);
  }

  void warmup() throws Exception {
    System.out.print("Warming up"); System.out.flush();
    try {
      System.out.println(" done.");
    } catch (Throwable t) { shutdown(); }
  }

  @Override public void run() {
    final long start = Util.now();
    final Script.Instance si = script.newInstance(client);
    try {
      new AsyncCompletionHandlerBase() {
        volatile RequestProvider rp;
        volatile int startSlot;
        void newRequest() throws IOException {
          rp = si.nextRequestProvider();
          if (rp == null) return;
          startSlot = rp.liveStats.registerReq();
          client.executeRequest(rp.request(si), this);
        }
        {newRequest();}
        @Override public Response onCompleted(Response resp) throws IOException {
          final boolean succ = isSuccessResponse(resp);
          rp.liveStats.deregisterReq(startSlot, succ);
          si.result(resp, succ);
          newRequest();
          return resp;
        }
        @Override public void onThrowable(Throwable t) {
          rp.liveStats.deregisterReq(startSlot, false);
          si.result(null, false);
        }
      };
      if (!sched.isShutdown())
        sched.schedule(this, 1_000_000_000/intensity - (now()-start), NANOSECONDS);
    } catch (Throwable t) {
      System.err.println("Error in request firing task.");
      t.printStackTrace();
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
      netty.shutdown();
    } catch (InterruptedException e) { }
  }

  static boolean isSuccessResponse(Response r) {
    return r.getStatusCode() >= 200 && r.getStatusCode() < 400;
  }
  static ProxyServer toProxyServer(String proxyString) {
    if (proxyString == null) return null;
    final String[] parts = proxyString.split(":");
    return new ProxyServer(parts[0], parts.length > 1? Integer.valueOf(parts[1]) : 80);
  }
  public static void main(String[] args) throws Exception {
    new StressTester(new Parser(new FileInputStream(args[0])).parse()).runTest();
  }
}
