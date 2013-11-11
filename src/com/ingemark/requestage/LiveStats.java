package com.ingemark.requestage;

import static com.ingemark.requestage.StressTester.HIST_SIZE;
import static com.ingemark.requestage.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.requestage.Util.encodeElapsedMillis;
import static com.ingemark.requestage.Util.toIndex;
import static java.util.concurrent.TimeUnit.SECONDS;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.util.concurrent.atomic.AtomicInteger;

public class LiveStats {
  private static final double NANOSEC_TO_SEC = 1.0/SECONDS.toNanos(1);
  private static final int
    RESPMETRICS_SIZE = 50, RESPMETRICS_SAMPLING_PERIOD = 2*(int)SECONDS.toNanos(1)/RESPMETRICS_SIZE;
  public final int index;
  public final String name;
  volatile Throwable lastException;
  private int respMetricsIndex, timeSlot = Integer.MAX_VALUE;
  final float[] respTimes = new float[RESPMETRICS_SIZE], respSizes = new float[RESPMETRICS_SIZE];
  final int[]
      reqs = new int[TIMESLOTS_PER_SEC],
      succs = new int[TIMESLOTS_PER_SEC],
      fails = new int[TIMESLOTS_PER_SEC];
  final AtomicInteger pendingReqs = new AtomicInteger();
  final TLongObjectHashMap<TIntIntHashMap> respHistory = new TLongObjectHashMap();
  private long respMetricsLastSampled;
  private final char[] histogram = new char[HIST_SIZE];

  LiveStats(int index, String name) { this.index = index; this.name = name; }

  synchronized int registerReq() {
    pendingReqs.incrementAndGet();
    histogram[toIndex(histogram, timeSlot)]++;
    reqs[toIndex(reqs, timeSlot)]++;
    return timeSlot;
  }
  synchronized void deregisterReq(
      int startSlot, long now, long start, long startMillis, int respSize, Throwable t)
  {
    pendingReqs.decrementAndGet();
    if (now-respMetricsLastSampled >= RESPMETRICS_SAMPLING_PERIOD) {
      respMetricsLastSampled = now;
      respTimes[respMetricsIndex % RESPMETRICS_SIZE] = (float)((now-start)*NANOSEC_TO_SEC);
      respSizes[respMetricsIndex++ % RESPMETRICS_SIZE] = respSize;
    }
    if (startSlot-timeSlot < histogram.length) histogram[toIndex(histogram, startSlot)]--;
    final int[] ary =  t == null? succs : fails;
    ary[toIndex(ary, timeSlot)]++;
    if (t != null) lastException = t;
    addRespHistory(startMillis/1000 * 1000, encodeElapsedMillis(now, start));
  }
  synchronized char[] reqHistogram() {
    final char[] hist = histogram, ret = new char[hist.length];
    final int ind = toIndex(hist, timeSlot);
    System.arraycopy(hist, ind, ret, 0, hist.length-ind);
    if (ind != 0) System.arraycopy(hist, 0, ret, hist.length-ind, ind);
    return ret;
  }
  synchronized Stats stats(int guiUpdateDivisor) {
    try {
      return timeSlot % guiUpdateDivisor == 0? new Stats(this) : null;
    } finally {
      timeSlot--;
      reqs[toIndex(reqs, timeSlot)] = succs[toIndex(succs, timeSlot)] =
          fails[toIndex(fails, timeSlot)] = histogram[toIndex(histogram, timeSlot)] = 0;
      respHistory.clear();
    }
  }
  private void addRespHistory(long start, int encodedElapsedMillis) {
    TIntIntHashMap ts = respHistory.get(start);
    if (ts == null) { ts = new TIntIntHashMap(); respHistory.put(start, ts); }
    ts.put(encodedElapsedMillis, 1 + ts.get(encodedElapsedMillis));
  }
}