package com.ingemark.perftest;

import static com.ingemark.perftest.Util.arraySum;
import static com.ingemark.perftest.Util.toIndex;
import static java.lang.Math.max;
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
  private static final int NS_TO_MS = 1_000_000;
  public static final int TIMESLOTS_PER_SEC = 20, HIST_SIZE = 200;
  final ScheduledExecutorService sched = Executors.newScheduledThreadPool(2);
  final Script script;
  final Random rnd = new Random();
  final Control eventReceiver;
  volatile int intensity = 1, refreshDivisor = 1;
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
      int refreshTimeslot = Integer.MIN_VALUE;
      volatile int[] refreshTimes;
      volatile int maxRefreshTime;
      {resetRefreshData();}
      void resetRefreshData() {
        refreshTimes = new int[(5 * TIMESLOTS_PER_SEC) / refreshDivisor];
        maxRefreshTime = (980 * refreshDivisor) / TIMESLOTS_PER_SEC;
      }
      public void run() {
        try {
          final List<Stats> stats = stats();
          if (stats.isEmpty()) return;
          final long enqueuedAt = now()/NS_TO_MS;
          Display.getDefault().asyncExec(new Runnable() {
            public void run() {
              final long start = now()/NS_TO_MS;
              if (eventReceiver.isDisposed()) return;
              for (Stats s : stats) {
                final Event e = new Event();
                e.data = s;
                eventReceiver.notifyListeners(StressTestActivator.STATS_EVTYPE_BASE + s.index, e);
              }
              final long end = now()/NS_TO_MS;
              final int elapsed = (int)(end-start), timeInQueue = (int)(start-enqueuedAt);
              refreshTimes[toIndex(refreshTimes, refreshTimeslot++)] = elapsed;
              final int avgRefresh = arraySum(refreshTimes)/refreshTimes.length;
              if (!adjustSlowGui(end, avgRefresh, timeInQueue))
                adjustFastGui(end, avgRefresh, timeInQueue);
              if (refreshTimeslot % ((5*TIMESLOTS_PER_SEC)/refreshDivisor) == 0)
                System.out.format("timeInQueue %d avgRefresh %d refreshDivisor %d\n",
                    timeInQueue, avgRefresh, refreshDivisor);
            }
            boolean adjustSlowGui(long now, int avgRefresh, int timeInQueue) {
              if (timeInQueue < 200) { guiSlowSince = 0; return false; }
              if (refreshDivisor >= TIMESLOTS_PER_SEC) return true;
              if (guiSlowSince == 0) { guiSlowSince = now; return true; }
              if (now-guiSlowSince > 5000) {
                guiSlowSince = 0;
                refreshDivisor = max(refreshDivisor+1, (avgRefresh*TIMESLOTS_PER_SEC)/1000);
                resetRefreshData();
                System.out.println("Reducing refresh rate");
              }
              return true;
            }
            void adjustFastGui(long now, int avgRefresh, long timeInQueue) {
              if (refreshDivisor <= 1) return;
              final double d = refreshDivisor;
              if (timeInQueue > 100 || avgRefresh * (d/(d-1)) > maxRefreshTime) {
                guiFastSince = 0; return;
              }
              if (guiFastSince == 0) { guiFastSince = now; return; }
              if (now-guiFastSince > 5000) {
                guiFastSince = 0;
                refreshDivisor--;
                resetRefreshData();
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
      final Stats stats = p.liveStats.stats(refreshDivisor);
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
