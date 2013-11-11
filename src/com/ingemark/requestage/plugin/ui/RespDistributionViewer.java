package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.Util.DIVS_PER_DECADE;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_HISTORY_UPDATE;
import static com.ingemark.requestage.plugin.RequestAgePlugin.globalEventHub;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.procedure.TIntIntProcedure;
import gnu.trove.procedure.TLongObjectProcedure;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.ISeriesSet;

import com.ingemark.requestage.Stats;

public class RespDistributionViewer implements Listener
{
  private static final String[] xLabels = new String[DIVS_PER_DECADE * 7]; static {
    int div = 0;
    for (int i = 0; i < xLabels.length; i++)
      xLabels[i] = i % DIVS_PER_DECADE == 0?
          String.valueOf(div++)
          : "";
  }
  private double[] dist = new double[4*DIVS_PER_DECADE];
  private Chart chart;

  private final TLongObjectProcedure<TIntIntHashMap> mergeIntoDistribution =
      new TLongObjectProcedure<TIntIntHashMap>() {
        @Override public boolean execute(long start, TIntIntHashMap times) {
          times.forEachEntry(new TIntIntProcedure() {
            @Override public boolean execute(int encodedElapsedMillis, int count) {
              ensureCapacity(encodedElapsedMillis);
              dist[encodedElapsedMillis] += count;
              return true;
          }});
          return true;
        }
  };


  RespDistributionViewer(Composite parent) {
    final Display disp = parent.getDisplay();
    parent.setLayout(new FillLayout());
    chart = new Chart(parent, SWT.NONE);
    chart.setBackground(disp.getSystemColor(SWT.COLOR_WHITE));
    chart.getLegend().setVisible(false);
    final ISeriesSet ss = chart.getSeriesSet();
    ss.createSeries(SeriesType.BAR, "elapsedMillisDist");
    final IAxis x = chart.getAxisSet().getXAxis(0);
    x.enableCategory(true);
    x.setCategorySeries(xLabels);
    globalEventHub().addListener(EVT_HISTORY_UPDATE, this);
  }

  @Override public void handleEvent(Event e) {
    chart.getSeriesSet().getSeries()[0].setYSeries(dist);
    chart.getAxisSet().adjustRange();
  }

  void statsUpdate(Stats stats) {
    stats.respHistory.forEachEntry(mergeIntoDistribution);
  }

  private void ensureCapacity(int requestedIndex) {
    if (requestedIndex < dist.length) return;
    dist = Arrays.copyOf(dist, (requestedIndex/DIVS_PER_DECADE + 1) * DIVS_PER_DECADE);
  }

}

