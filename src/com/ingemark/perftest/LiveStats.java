package com.ingemark.perftest;

import static com.ingemark.perftest.StressTester.HIST_SIZE;
import static com.ingemark.perftest.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.perftest.Util.toIndex;

import java.util.concurrent.atomic.AtomicInteger;

public class LiveStats {
  public final int index;
  public final String name;
  volatile Throwable lastException;
  final char[] histogram = new char[HIST_SIZE];
  final int[]
      reqs = new int[TIMESLOTS_PER_SEC],
      succs = new int[TIMESLOTS_PER_SEC],
      fails = new int[TIMESLOTS_PER_SEC];
  final AtomicInteger pendingReqs = new AtomicInteger();
  int timeSlot = Integer.MAX_VALUE;

  LiveStats(int index, String name) { this.index = index; this.name = name; }

  synchronized int registerReq() {
    pendingReqs.incrementAndGet();
    histogram[toIndex(histogram, timeSlot)]++;
    reqs[toIndex(reqs, timeSlot)]++;
    return timeSlot;
  }
  synchronized void deregisterReq(int startSlot, Throwable t) {
    pendingReqs.decrementAndGet();
    final char[] hist = histogram;
    if (startSlot-timeSlot < hist.length) hist[toIndex(hist, startSlot)]--;
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