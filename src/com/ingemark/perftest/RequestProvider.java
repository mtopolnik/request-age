package com.ingemark.perftest;

import static com.ingemark.perftest.StressTester.HIST_SIZE;
import static com.ingemark.perftest.StressTester.TIMESLOTS_PER_SEC;

import java.util.concurrent.atomic.AtomicInteger;

import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Request;

public class RequestProvider {
  public final String name, method, url, body;
  public final LiveStats liveStats;
  public RequestProvider(int index, String name, String method, String url, String body) {
    this.liveStats = new LiveStats(index);
    this.name = name; this.method = method; this.url = url; this.body = body;
  }
  public Request request(Script.Instance si) {
    final BoundRequestBuilder b = si.client.prepareConnect(url).setMethod(method);
    return (body != null? b.setBody(body) : b).build();
  }

  public class LiveStats {
    public final int index;
    final int[]
        histogram = new int[HIST_SIZE],
        reqs = new int[TIMESLOTS_PER_SEC],
        succs = new int[TIMESLOTS_PER_SEC],
        fails = new int[TIMESLOTS_PER_SEC];
    final AtomicInteger pendingReqs = new AtomicInteger();
    int timeSlot = Integer.MAX_VALUE;

    LiveStats(int index) { this.index = index; }

    synchronized int registerReq() {
      pendingReqs.incrementAndGet();
      histogram[toIndex(histogram, timeSlot)]++;
      reqs[toIndex(reqs, timeSlot)]++;
      return timeSlot;
    }
    synchronized void deregisterReq(int startSlot, boolean success) {
      pendingReqs.decrementAndGet();
      final int[] hist = histogram;
      if (startSlot-timeSlot < hist.length) hist[toIndex(hist, startSlot)]--;
      final int[] ary = success?succs:fails;
      ary[toIndex(ary, timeSlot)]++;
    }
    synchronized int[] reqHistogram() {
      final int[] hist = histogram, ret = new int[hist.length];
      final int ind = toIndex(hist, timeSlot);
      System.arraycopy(hist, ind, ret, 0, hist.length-ind);
      if (ind != 0) System.arraycopy(hist, 0, ret, hist.length-ind, ind);
      return ret;
    }
    synchronized Stats stats(int guiUpdateDivisor) {
      try {
        return timeSlot % guiUpdateDivisor == 0? new Stats(this, name) : null;
      } finally {
        timeSlot--;
        final int histIndex = toIndex(histogram, timeSlot);
        histogram[histIndex] =
            reqs[toIndex(reqs, timeSlot)] = succs[toIndex(succs, timeSlot)] =
            fails[toIndex(fails, timeSlot)] = 0;
      }
    }
  }
  static int toIndex(int[] array, int timeSlot) {
    int slot = timeSlot%array.length;
    return slot >= 0? slot : slot + array.length;
  }
}