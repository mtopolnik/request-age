package com.ingemark.requestage;

import static com.ingemark.requestage.StressTester.HIST_SIZE;
import static com.ingemark.requestage.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.requestage.Util.toIndex;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.atomic.AtomicInteger;

public class LiveStats {
  private static final double NANOSEC_TO_SEC = 1.0/SECONDS.toNanos(1);
  private static final int RESPTIMES_SIZE = 35;
  public final int index;
  public final String name;
  volatile Throwable lastException;
  private int respTimesIndex, timeSlot = Integer.MAX_VALUE;
  final float[] respTimes = new float[RESPTIMES_SIZE];
  final int[]
      reqs = new int[TIMESLOTS_PER_SEC],
      succs = new int[TIMESLOTS_PER_SEC],
      fails = new int[TIMESLOTS_PER_SEC];
  final AtomicInteger pendingReqs = new AtomicInteger();
  private long respTimeLastSampled;
  private final char[] histogram = new char[HIST_SIZE];

  LiveStats(int index, String name) { this.index = index; this.name = name; }

  synchronized int registerReq() {
    pendingReqs.incrementAndGet();
    histogram[toIndex(histogram, timeSlot)]++;
    reqs[toIndex(reqs, timeSlot)]++;
    return timeSlot;
  }
  synchronized void deregisterReq(int startSlot, long now, long start, Throwable t) {
    pendingReqs.decrementAndGet();
    if (now-respTimeLastSampled >= SECONDS.toNanos(1)/RESPTIMES_SIZE) {
      respTimeLastSampled = now;
      respTimes[respTimesIndex++ % RESPTIMES_SIZE] = (float)((now-start)*NANOSEC_TO_SEC);
    }
    if (startSlot-timeSlot < histogram.length) histogram[toIndex(histogram, startSlot)]--;
    final int[] ary =  t == null? succs : fails;
    ary[toIndex(ary, timeSlot)]++;
    if (t != null) lastException = t;
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
    }
  }
}