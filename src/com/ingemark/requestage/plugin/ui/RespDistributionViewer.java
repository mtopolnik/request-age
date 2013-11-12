package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.Util.DIVS_PER_DECADE;
import static com.ingemark.requestage.Util.color;
import static com.ingemark.requestage.Util.now;
import static com.ingemark.requestage.plugin.ui.RequestAgeView.gridColor;
import static java.util.concurrent.TimeUnit.SECONDS;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.procedure.TIntIntProcedure;
import gnu.trove.procedure.TLongObjectProcedure;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.IAxisSet;
import org.swtchart.ILineSeries;
import org.swtchart.ILineSeries.PlotSymbolType;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.ISeriesSet;
import org.swtchart.ITitle;

import com.ingemark.requestage.Stats;

public class RespDistributionViewer
{
  Chart chart;
  private long[] dist = {};
  private long totalCount, lastUpdate;

  private final TLongObjectProcedure<TIntIntHashMap> mergeIntoDistribution =
      new TLongObjectProcedure<TIntIntHashMap>() {
        @Override public boolean execute(long start, TIntIntHashMap times) {
          times.forEachEntry(new TIntIntProcedure() {
            @Override public boolean execute(int encodedElapsedMillis, int count) {
              ensureCapacity(encodedElapsedMillis);
              dist[encodedElapsedMillis] += count;
              totalCount += count;
              return true;
          }});
          return true;
        }
  };

  RespDistributionViewer(Composite parent) {
    final Display disp = parent.getDisplay();
    chart = new Chart(parent, SWT.NONE);
    final Color black = color(SWT.COLOR_BLACK), white = disp.getSystemColor(SWT.COLOR_WHITE);
    chart.setBackground(white);
    chart.getLegend().setVisible(false);
    chart.getTitle().setVisible(false);
    final IAxisSet axes = chart.getAxisSet();
    final IAxis x = axes.getXAxis(0);
    x.getTick().setForeground(black);
    final ITitle xTitle = x.getTitle();
    xTitle.setFont(disp.getSystemFont());
    xTitle.setForeground(black);
    xTitle.setText("log(resp_time)");
    x.getGrid().setForeground(gridColor);
    final IAxis y = axes.getYAxis(0);
    y.getTick().setForeground(black);
    final ITitle yTitle = y.getTitle();
    yTitle.setFont(disp.getSystemFont());
    yTitle.setForeground(black);
    yTitle.setText("% total");
    x.getGrid().setForeground(gridColor);
    final ISeriesSet ss = chart.getSeriesSet();
    final ILineSeries ser = (ILineSeries) ss.createSeries(SeriesType.LINE, "elapsedMillisDist");
    ser.setSymbolType(PlotSymbolType.NONE);
    ser.enableArea(true);
    ensureCapacity(80);
  }

  void statsUpdate(Stats stats) {
    stats.respHistory.forEachEntry(mergeIntoDistribution);
    final double[] ySeries = new double[dist.length];
    for (int i = 0; i < ySeries.length; i++) ySeries[i] = 100L*dist[i]/(double)totalCount;
    chart.getSeriesSet().getSeries()[0].setYSeries(ySeries);
    if (now() - lastUpdate > SECONDS.toNanos(2)) {
      chart.getAxisSet().adjustRange();
      chart.redraw();
      lastUpdate = now();
    }
  }

  private void ensureCapacity(int requestedIndex) {
    if (requestedIndex < dist.length) return;
    dist = Arrays.copyOf(dist, (requestedIndex/DIVS_PER_DECADE + 1) * DIVS_PER_DECADE);
    final double[] xSeries = new double[dist.length];
    for (int i = 0; i < xSeries.length; i++) xSeries[i] = i/(double)DIVS_PER_DECADE - 3;
    chart.getSeriesSet().getSeries()[0].setXSeries(xSeries);
  }
}

