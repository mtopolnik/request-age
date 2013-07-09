package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.Util.color;
import static com.ingemark.requestage.Util.gridData;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_HISTORY_UPDATE;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_INIT_HIST;
import static com.ingemark.requestage.plugin.RequestAgePlugin.globalEventHub;
import static org.eclipse.swt.SWT.FILL;
import static org.swtchart.ISeries.SeriesType.LINE;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.part.ViewPart;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.IAxisSet;
import org.swtchart.ILineSeries;
import org.swtchart.ILineSeries.PlotSymbolType;
import org.swtchart.ISeries;
import org.swtchart.ISeriesSet;
import org.swtchart.ITitle;

public class HistoryView extends ViewPart implements Listener
{
  private static final int[] colors = { SWT.COLOR_BLUE, SWT.COLOR_GREEN, SWT.COLOR_RED,
    SWT.COLOR_CYAN, SWT.COLOR_MAGENTA, SWT.COLOR_YELLOW };
  private final Color gridColor = new Color(Display.getCurrent(), 220, 220, 220);
  private int color;
  private Chart chart;

  @Override public void createPartControl(Composite parent) {
    chart = new Chart(parent, SWT.NONE);
    final Display disp = parent.getDisplay();
    chart.setBackground(disp.getSystemColor(SWT.COLOR_WHITE));
    gridData().align(FILL, FILL).grab(true, true).applyTo(chart);
    chart.getTitle().setVisible(false);
    final IAxisSet axes = chart.getAxisSet();
    final IAxis y = axes.getYAxis(0);
    y.getTick().setForeground(color(SWT.COLOR_BLACK));
    y.enableLogScale(true);
    final ITitle yTitle = y.getTitle();
    yTitle.setText("1/resp_time");
    yTitle.setFont(disp.getSystemFont());
    yTitle.setForeground(color(SWT.COLOR_BLACK));
    y.getGrid().setForeground(gridColor);
    final IAxis x = axes.getXAxis(0);
    x.getTick().setForeground(color(SWT.COLOR_BLACK));
    x.getTitle().setVisible(false);
    x.getGrid().setForeground(gridColor);
    globalEventHub().addListener(EVT_INIT_HIST, this);
    globalEventHub().addListener(EVT_HISTORY_UPDATE, this);
  }

  @Override public void handleEvent(Event event) {
    switch (event.type) {
    case EVT_INIT_HIST:
      final ISeriesSet ss = chart.getSeriesSet();
      for (ISeries s : ss.getSeries()) ss.deleteSeries(s.getId());
      break;
    case EVT_HISTORY_UPDATE:
      update((History) event.data);
      break;
    }
  }

  private void update(History h) {
    final ISeriesSet ss = chart.getSeriesSet();
    ILineSeries ser = (ILineSeries) ss.getSeries(h.name);
    if (ser == null) {
      ser = (ILineSeries) ss.createSeries(LINE, h.name);
      ser.setLineColor(color(colors[color++ % colors.length]));
    }
    ser.setSymbolType(PlotSymbolType.NONE);
    ser.setYSeries(h.servingIntensity());
    ser.setXDateSeries(h.timestamps());
    chart.getAxisSet().adjustRange();
    chart.redraw();
  }

  @Override public void setFocus() { }

  @Override public void dispose() {
    globalEventHub().removeListener(EVT_INIT_HIST, this);
    globalEventHub().removeListener(EVT_HISTORY_UPDATE, this);
  }

  public static void main(String[] args) {
    final Display d = Display.getDefault();
    final Shell s = new Shell(d);
    s.setLayout(new GridLayout(1,true));
    new HistoryView().createPartControl(s);
    s.open();
    while (!s.isDisposed()) if (!d.readAndDispatch()) d.sleep();
    d.dispose();
  }
}
