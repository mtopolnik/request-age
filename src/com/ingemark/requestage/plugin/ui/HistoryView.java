package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.Util.gridData;
import static org.eclipse.swt.SWT.FILL;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.part.ViewPart;
import org.swtchart.Chart;
import org.swtchart.IAxisSet;
import org.swtchart.ISeries;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.ISeriesSet;
import org.swtchart.Range;

public class HistoryView extends ViewPart
{
  public HistoryView() {}

  @Override public void createPartControl(Composite parent) {
    final Chart chart = new Chart(parent, SWT.NONE);
    chart.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
    gridData().align(FILL, FILL).grab(true, true).applyTo(chart);
    double[] ySeries = { 0.3, 1.4, 1.3, 1.9, 2.1 };
    final ISeriesSet seriesSet = chart.getSeriesSet();
    final ISeries series = seriesSet.createSeries(SeriesType.LINE, "line series");
    series.setYSeries(ySeries);
    final IAxisSet axisSet = chart.getAxisSet();
    axisSet.getXAxes()[0].setRange(new Range(0,10));
    axisSet.getYAxes()[0].adjustRange();
  }

  @Override public void setFocus() {

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
