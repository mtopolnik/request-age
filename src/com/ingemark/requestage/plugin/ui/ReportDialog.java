package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.Util.gridData;
import static java.text.DateFormat.MEDIUM;
import static java.text.DateFormat.getDateTimeInstance;
import static org.eclipse.swt.SWT.FILL;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.swtchart.Chart;

import com.ibm.icu.text.DecimalFormat;
import com.ingemark.requestage.Stats;

public class ReportDialog
{
  private static final String[] headers = {
    "name","req rate","succ rate","fail rate","resp time","resp stdev"};
  private static final String format; static {
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < 6; i++) b.append("%s\t");
    format = b.append("\n").toString();
  }
  private static final DateFormat dateTimeFormat = getDateTimeInstance(MEDIUM, MEDIUM);
  private static final DecimalFormat respTimeFormat = new DecimalFormat("@##");

  static void show(String testName, List<Stats> statsList) {
    final Display disp = Display.getCurrent();
    final Shell top = new Shell(disp);
    final Rectangle bounds = disp.map(RequestAgeView.instance.statsParent, null,
        RequestAgeView.instance.statsParent.getBounds());
    top.setBounds(bounds);
    top.setLayout(new GridLayout(1, false));
    top.setText(testName + " - RequestAge Report");
    final Label lDateTime = new Label(top, SWT.NONE);
    gridData().align(FILL, FILL).applyTo(lDateTime);
    lDateTime.setText(dateTimeFormat.format(new Date()));
    final Table t = new Table(top, SWT.H_SCROLL | SWT.V_SCROLL);
    gridData().align(FILL, FILL)
    .applyTo(t);
    t.setLinesVisible(true);
    t.setHeaderVisible(true);
    gridData().align(FILL, FILL)
    .grab(true, true)
    .applyTo(new Chart(top, SWT.NONE));
    final Button ok = new Button(top, SWT.NONE);
    top.setDefaultButton(ok);
    ok.setText("OK");
    gridData().align(SWT.RIGHT, SWT.FILL).applyTo(ok);

    top.addListener(SWT.Traverse, new Listener() { public void handleEvent(Event event) {
      if (event.detail != SWT.TRAVERSE_ESCAPE) return;
      top.close();
      event.detail = SWT.TRAVERSE_NONE;
      event.doit = false;
    }});
    ok.addSelectionListener(new SelectionListener() {
      @Override public void widgetSelected(SelectionEvent e) { top.close(); }
      @Override public void widgetDefaultSelected(SelectionEvent e) {}
    });

    for (String h: headers) {
      final TableColumn col = new TableColumn(t, SWT.NONE);
      col.setText(h);
    }
    for (Stats stats : statsList) {
      final TableItem it = new TableItem(t, SWT.NONE);
      int i = 0;
      it.setText(i++, stats.name);
      it.setText(i++, ""+stats.reqsPerSec);
      it.setText(i++, ""+stats.succRespPerSec);
      it.setText(i++, ""+stats.failsPerSec);
      it.setText(i++, timeFormat(stats.avgRespTime));
      it.setText(i++, timeFormat(stats.stdevRespTime));
    }
    for (int i = 0; i < headers.length; i++) t.getColumn(i).pack();

    top.pack();
    top.setVisible(true);
    top.setFocus();
  }

  private static String timeFormat(float f) {
    return f >= 1? respTimeFormat.format(f) + " s" : respTimeFormat.format(f*1000)+" ms";
  }

  private static Object[] reportRow(Stats stats) {
    return new Object[] {stats.name, stats.reqsPerSec, stats.succRespPerSec,
        stats.failsPerSec, stats.avgRespTime, stats.stdevRespTime};
  }

  private static String textReport(String testName, List<Stats> statsList) {
    final StringWriter w = new StringWriter();
    final PrintWriter pw = new PrintWriter(w);
    pw.format("RequestAge Report for %s on %s\n", testName, dateTimeFormat.format(new Date()));
    String sep = "";
    for (String h : headers) { pw.append(sep).append(h.replace(' ','_')); sep = "\t"; }
    pw.println();
    for (Stats stats : statsList) pw.format(format, reportRow(stats));
    return pw.toString();
  }
}
