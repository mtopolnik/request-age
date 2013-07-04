package com.ingemark.requestage;

import static com.ingemark.requestage.StressTester.HIST_SIZE;
import static com.ingemark.requestage.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.requestage.Util.toIndex;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.atomic.AtomicInteger;

public class LiveStats {
  private static final int TIMES_SIZE = 50;
  public final int index;
  public final String name;
  volatile Throwable lastException;
  private final char[] histogram = new char[HIST_SIZE];
  private int respTimesIndex, timeSlot = Integer.MAX_VALUE;
  final int[]
      respTimes = new int[TIMES_SIZE],
      reqs = new int[TIMESLOTS_PER_SEC],
      succs = new int[TIMESLOTS_PER_SEC],
      fails = new int[TIMESLOTS_PER_SEC];
  final AtomicInteger pendingReqs = new AtomicInteger();

  LiveStats(int index, String name) { this.index = index; this.name = name; }

  synchronized int registerReq() {
    pendingReqs.incrementAndGet();
    histogram[toIndex(histogram, timeSlot)]++;
    reqs[toIndex(reqs, timeSlot)]++;
    return timeSlot;
  }
  synchronized void deregisterReq(int startSlot, long time, Throwable t) {
    pendingReqs.decrementAndGet();
    respTimes[respTimesIndex++ % TIMES_SIZE] = (int)NANOSECONDS.toMillis(time);
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
      final int histIndex = toIndex(histogram, timeSlot);
      histogram[histIndex] = (char)
          (reqs[toIndex(reqs, timeSlot)] =
          succs[toIndex(succs, timeSlot)] =
          fails[toIndex(fails, timeSlot)] = 0);
    }
  }
}