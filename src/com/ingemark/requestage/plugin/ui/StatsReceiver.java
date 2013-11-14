package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.requestage.Util.event;
import static com.ingemark.requestage.Util.now;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_STATS;
import static com.ingemark.requestage.plugin.RequestAgePlugin.globalEventHub;
import static com.ingemark.requestage.plugin.ui.RequestAgeView.log;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import org.eclipse.swt.widgets.Display;

import com.ingemark.requestage.StatsHolder;

public class StatsReceiver
{
  private int refreshDivisor = 1;
  private volatile int refreshTimeslot = Integer.MIN_VALUE;
  private volatile long guiSlowSince, guiFastSince;

  public void receiveStats(final StatsHolder statsHolder) {
    final long enqueuedAt = NANOSECONDS.toMillis(now());
    Display.getDefault().asyncExec(new Runnable() {
      public void run() {
        final long now = NANOSECONDS.toMillis(now());
        globalEventHub().notifyListeners(EVT_STATS, event(statsHolder));
        final int timeInQueue = (int)(now-enqueuedAt);
        if (!adjustSlowGui(now, timeInQueue))
          adjustFastGui(now, timeInQueue);
        if (refreshTimeslot % ((5*TIMESLOTS_PER_SEC)/refreshDivisor) == 0)
          log.debug("timeInQueue {} refreshDivisor {}", timeInQueue, refreshDivisor);
      }
      private boolean adjustSlowGui(long now, int timeInQueue) {
        if (timeInQueue < 200) { guiSlowSince = 0; return false; }
        if (refreshDivisor >= TIMESLOTS_PER_SEC) return true;
        if (guiSlowSince == 0) { guiSlowSince = now; return true; }
        if (now-guiSlowSince > 5000) {
          guiSlowSince = 0;
          refreshDivisor++;
          log.debug("Reducing refresh rate");
        }
        return true;
      }
      private void adjustFastGui(long now, long timeInQueue) {
        if (refreshDivisor <= 1) return;
        if (timeInQueue > 100) { guiFastSince = 0; return; }
        if (guiFastSince == 0) { guiFastSince = now; return; }
        if (now-guiFastSince > 5000) {
          guiFastSince = 0;
          refreshDivisor--;
          log.debug("Increasing refresh rate");
        }
      }
    });
  }
}
