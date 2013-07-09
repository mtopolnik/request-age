package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.Util.gridData;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_HISTORY_UPDATE;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_INIT_HIST;
import static com.ingemark.requestage.plugin.RequestAgePlugin.globalEventHub;
import static org.eclipse.swt.SWT.FILL;
import static org.swtchart.ISeries.SeriesType.LINE;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.part.ViewPart;
import org.swtchart.Chart;
import org.swtchart.ILineSeries;
import org.swtchart.ILineSeries.PlotSymbolType;
import org.swtchart.ISeries;
import org.swtchart.ISeriesSet;

public class HistoryView extends ViewPart implements Listener
{
  private Chart chart;

  public HistoryView() {}

  @Override public void createPartControl(Composite parent) {
    chart = new Chart(parent, SWT.NONE);
    chart.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
    gridData().align(FILL, FILL).grab(true, true).applyTo(chart);
    chart.getAxisSet().getYAxis(0).enableLogScale(true);
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
    if (ser == null) ser = (ILineSeries) ss.createSeries(LINE, h.name);
    ser.setSymbolType(PlotSymbolType.NONE);
    ser.setYSeries(h.reqsPerSec());
    ser.setXDateSeries(h.timestamps());
    chart.getAxisSet().adjustRange();
    chart.redraw();
  }

  @Override public void setFocus() {

  }

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
