package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.requestage.Util.arrayMean;
import static com.ingemark.requestage.Util.decodeElapsedMillis;
import static com.ingemark.requestage.Util.event;
import static com.ingemark.requestage.Util.sneakyThrow;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_HISTORY_UPDATE;
import static com.ingemark.requestage.plugin.RequestAgePlugin.globalEventHub;
import static java.util.Collections.nCopies;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.procedure.TLongObjectProcedure;
import gnu.trove.procedure.TLongProcedure;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.set.hash.TLongHashSet;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingemark.requestage.Stats;

public class History {
  static final Logger log = LoggerFactory.getLogger(History.class);
  private static final int FULL_SIZE = 1<<11, BUFSIZ = 8*TIMESLOTS_PER_SEC;
  public static final String[] keys = {
    "avgRespTime", "pendingReqs", "reqsPerSec", "failsPerSec"};
  private static final Map<String, Field> statFields = new HashMap<String, Field>(); static {
    try { for (String key : keys) statFields.put(key, Stats.class.getField(key)); }
    catch (Exception e) { sneakyThrow(e); }
  }
  public String name;
  private int index, bufIndex, bufLimit = TIMESLOTS_PER_SEC, divisor = 1, calledCount;
  private final Map<String, double[]> histories = new HashMap<String, double[]>(); {
    for (String key : keys) histories.put(key, new double[FULL_SIZE]);
  }
  private final Map<String, float[]> histBufs = new HashMap<String, float[]>(); {
    for (String key : keys) histBufs.put(key, new float[BUFSIZ]);
  }
  private final Date[] timestamps = new Date[FULL_SIZE];
  private final TLongObjectHashMap<TIntHashSet> respHistory =
      new TLongObjectHashMap<TIntHashSet>(FULL_SIZE);
  private final TLongObjectProcedure<TIntHashSet> mergeIntoHistory =
      new TLongObjectProcedure<TIntHashSet>() {
        @Override public boolean execute(long start, TIntHashSet times) {
          final int divisor = effectiveDivisor();
          final TIntHashSet ts = respHistEntry(start / divisor * divisor);
          times.forEach(new TIntProcedure() { @Override public boolean execute(int encoded) {
            ts.add(decodeElapsedMillis(encoded)); return true;
          }});
          return true;
        }
  };

  public void statsUpdate(Stats stats) {
    stats.respHistory.forEachEntry(mergeIntoHistory);
    if (++calledCount % divisor != 0) return;
    name = stats.name;
    try {
      for (String key : keys) histBufs.get(key)[bufIndex] = statFields.get(key).getFloat(stats);
    } catch (Exception e) { sneakyThrow(e); }
    if (++bufIndex == bufLimit) {
      bufIndex = 0;
      timestamps[index] = new Date();
      for (String key : keys) histories.get(key)[index] = arrayMean(histBufs.get(key), bufLimit);
      globalEventHub().notifyListeners(EVT_HISTORY_UPDATE, event(this));
      if (++index == FULL_SIZE) {
        squeezeDates(timestamps);
        for (String key : keys) squeeze(histories.get(key));
        index /= 2;
        if (bufLimit <= BUFSIZ/2) bufLimit *= 2;
        else divisor *= 2;
        squeezeRespHistory();
      }
    }
  }
  private static void squeeze(double[] array) {
    for (int i = 0; i < array.length/2; i++) array[i] = (array[2*i]+array[2*i+1])/2;
  }
  private static void squeezeDates(Date[] array) {
    for (int i = 0; i < array.length/2; i++)
      array[i] = new Date((array[2*i].getTime()+array[2*i+1].getTime())/2);
  }
  private void squeezeRespHistory() {
    final TLongHashSet toRemove = new TLongHashSet();
    final int divisor = effectiveDivisor();
    respHistory.forEachEntry(new TLongObjectProcedure<TIntHashSet>() {
      @Override public boolean execute(long timestamp, TIntHashSet times) {
        if (timestamp % divisor == 0) return true;
        toRemove.add(timestamp);
        final long newKey = timestamp / divisor * divisor;
        final TIntHashSet ts = respHistEntry(newKey);
        ts.addAll(times);
        return true;
      }});
    toRemove.forEach(new TLongProcedure() { @Override public boolean execute(long t) {
      respHistory.remove(t);
      return true;
    }});
  }
  private int effectiveDivisor() { return divisor * (bufLimit / TIMESLOTS_PER_SEC); }

  private TIntHashSet respHistEntry(long timestamp) {
    TIntHashSet ts = respHistory.get(timestamp);
    if (ts == null) { ts = new TIntHashSet(); respHistory.put(timestamp, ts); }
    return ts;
  }

  public double[] history(String id) {
    return Arrays.copyOf(histories.get(id), index);
  }
  public Date[] timestamps() { return Arrays.copyOf(timestamps, index); }

  public RespTimeHistory respTimeHistory() {
    final List<Date> timestamps = new ArrayList<Date>(2*respHistory.size());
    final TDoubleArrayList timeList = new TDoubleArrayList(2*respHistory.size());
    respHistory.forEachEntry(new TLongObjectProcedure<TIntHashSet>() {
      @Override public boolean execute(long timestamp, TIntHashSet times) {
        timestamps.addAll(nCopies(times.size(), new Date(timestamp)));
        times.forEach(new TIntProcedure() { @Override public boolean execute(int t) {
          timeList.add(t/1000.0); return true;
        }});
        return true;
      }
    });
    return new RespTimeHistory(
        timestamps.toArray(new Date[timestamps.size()]),
        timeList.toArray());
  }

  static class RespTimeHistory {
    Date[] timestamps;
    double[] times;
    RespTimeHistory(Date[] timestamps, double[] times) {
      this.timestamps = timestamps; this.times = times;
    }
  }
}
