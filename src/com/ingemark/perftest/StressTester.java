package com.ingemark.perftest;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;

import com.ingemark.perftest.plugin.StressTestActivator;
import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Response;

public class StressTester implements Runnable, IStressTester
{
  public static final int TIMESLOTS_PER_SEC = 20, HIST_SIZE = 200;
  final ScheduledExecutorService sched = Executors.newScheduledThreadPool(2);
  final Script script;
  final Random rnd = new Random();
  final Control eventReceiver;
  volatile int intensity = 1, guiUpdateDivisor = 1;
  volatile long guiSlowSince, guiFastSince;
  AsyncHttpClient client;
  volatile ScheduledFuture<?> nettyReportingTask;

  public StressTester(Control eventReceiver, Script script) {
    this.eventReceiver = eventReceiver;
    final AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
    b.setProxyServer(toProxyServer(script.initEnv.get("system.proxy")));
    this.client = new AsyncHttpClient();
    this.script = script;
  }

  public void setIntensity(int intensity) { this.intensity = intensity; }

  public void runTest() throws Exception {
    warmup();
    run();
    sched.scheduleAtFixedRate(new Runnable() {
      int updateCount;
      public void run() {
        try {
          final List<Stats> stats = stats();
          if (stats.isEmpty()) return;
          final long enqueuedAt = now();
          Display.getDefault().asyncExec(new Runnable() {
            final long maxRefreshTime = (980_000_000L * guiUpdateDivisor) / TIMESLOTS_PER_SEC;
            public void run() {
              final long start = now();
              if (eventReceiver.isDisposed()) return;
              for (Stats s : stats) {
                final Event e = new Event();
                e.data = s;
                eventReceiver.notifyListeners(StressTestActivator.STATS_EVTYPE_BASE + s.index, e);
              }
              final long end = now(), elapsed = end-start, timeInQueue = start-enqueuedAt;
              if (++updateCount % 50 == 0)
                System.out.println("Repaint time: " + elapsed/1_000_000 +
                    ", time in queue: " + timeInQueue/1_000_000 +
                    ", refresh divisor: " + guiUpdateDivisor);
              if (!adjustSlowGui(end, elapsed, timeInQueue))
                adjustFastGui(end, elapsed, timeInQueue);
            }
            boolean adjustSlowGui(long now, long elapsed, long timeInQueue) {
              if (timeInQueue < 500_000_000L) { guiSlowSince = 0; return false; }
              if (guiUpdateDivisor >= TIMESLOTS_PER_SEC) return true;
              if (guiSlowSince == 0) { guiSlowSince = now; return true; }
              if (now-guiSlowSince > 5_000_000_000L) {
                guiSlowSince = 0;
                guiUpdateDivisor++;
                System.out.println("Reducing refresh rate");
              }
              return true;
            }
            void adjustFastGui(long now, long elapsed, long timeInQueue) {
              if (guiUpdateDivisor <= 1) return;
              final double d = guiUpdateDivisor;
              if (elapsed * (d/(d-1)) > maxRefreshTime || timeInQueue > 10_000_000L) {
                guiFastSince = 0; return;
              }
              if (guiFastSince == 0) { guiFastSince = now; return; }
              if (now-guiFastSince > 5_000_000_000L) {
                guiFastSince = 0;
                guiUpdateDivisor--;
                System.out.println("Increasing refresh rate");
              }
            }
          });
        } catch (Throwable t) {
          System.err.println("Error in state reporting task");
          t.printStackTrace();
        }
      }
    }, 100, 1_000_000/TIMESLOTS_PER_SEC, MICROSECONDS);
    }

  void warmup() throws Exception {
    System.out.print("Warming up"); System.out.flush();
    try {
//      long prevTime = Long.MAX_VALUE;
//      for (int i = 0; i < 10; i++) {
//        final long start = now();
//        final Response r = client.executeRequest(req).get(10, SECONDS);
//        final long rt = now() - start;
//        if (!isSuccessResponse(r))
//          throw new RuntimeException("Request failed during warmup with status " +
//              r.getStatusCode() + " " + r.getStatusText());
//        System.out.print("."); System.out.flush();
//        if (1.2 * rt > prevTime) break;
//        prevTime = rt;
//      }
      System.out.println(" done.");
    } catch (Throwable t) { shutdown(); }
  }

  @Override public void run() {
    final long start = now();
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
        sched.schedule(this, /*100_000*i*/ 1_000_000_000/intensity - (now()-start), NANOSECONDS);
    } catch (Throwable t) {
      System.err.println("Error in request firing task.");
      t.printStackTrace();
    }
  }

  List<Stats> stats() {
    final List<Stats> ret = new ArrayList<>(script.testReqs.size());
    for (RequestProvider p : script.testReqs) {
      final Stats stats = p.liveStats.stats(guiUpdateDivisor);
      if (stats != null) ret.add(stats);
    }
    return ret;
  }

  public void shutdown() {
    try {
      sched.shutdown();
      sched.awaitTermination(5, SECONDS);
      client.close();
    } catch (InterruptedException e) { }
  }

  static boolean isSuccessResponse(Response r) {
    return r.getStatusCode() >= 200 && r.getStatusCode() < 400;
  }
  public static long now() { return System.nanoTime(); }

  static ProxyServer toProxyServer(String proxyString) {
    if (proxyString == null) return null;
    final String[] parts = proxyString.split(":");
    return new ProxyServer(parts[0], parts.length > 1? Integer.valueOf(parts[1]) : 80);
  }

  public static final IStressTester NULL = new IStressTester() {
    public void setIntensity(int intensity) { }
    public void shutdown() { }
    public void runTest() { }
  };
}
