package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.Util.gridData;
import static org.eclipse.ui.PlatformUI.getWorkbench;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IWorkbenchWindow;

import com.ingemark.requestage.Stats;

public class ReportDialog
{
  private static final String[] headers = {
    "name","req rate","succ resp rate","fail resp rate","resp time","resp stdev"};
  private static final String format; static {
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < 6; i++) b.append("%s\t");
    format = b.append("\n").toString();
  }

  static void show(String testName, List<Stats> statsList) {
    final Display disp = Display.getCurrent();
    final Shell top = new Shell(disp);
    final IWorkbenchWindow win = getWorkbench().getActiveWorkbenchWindow();
    final Rectangle mainBounds = disp.getBounds();
    final Rectangle bounds = win != null? win.getShell().getBounds() : mainBounds;
    bounds.x += 20; bounds.y += 20; bounds.height -= 40; bounds.width = mainBounds.width;
    top.setBounds(bounds);
    top.setLayout(new GridLayout(1, false));
    top.setText(testName + " -- RequestAge Report");
    final Table t = new Table(top, SWT.H_SCROLL | SWT.V_SCROLL);
    gridData().align(SWT.FILL, SWT.FILL).grab(true, true).applyTo(t);
    t.setLinesVisible(true);
    t.setHeaderVisible(true);
    final Button ok = new Button(top, SWT.NONE);
    top.setDefaultButton(ok);
    ok.setText("OK");
    gridData().align(SWT.RIGHT, SWT.FILL).applyTo(ok);

    for (String h: headers) {
      final TableColumn col = new TableColumn(t, SWT.NONE);
      col.setText(h);
    }
    for (Stats stats : statsList) {
      final TableItem it = new TableItem(t, SWT.NONE);
      final Object[] row = reportRow(stats);
      for (int i = 0; i < row.length; i++) it.setText(i, row[i].toString());
    }
    for (int i = 0; i < headers.length; i++) t.getColumn(i).pack();

    top.setVisible(true);
    top.setFocus();
  }

  private static Object[] reportRow(Stats stats) {
    return new Object[] {stats.name, stats.reqsPerSec, stats.succRespPerSec,
        stats.failsPerSec, stats.avgRespTime, stats.stdevRespTime};
  }

  private static String textReport(String testName, List<Stats> statsList) {
    final StringWriter w = new StringWriter();
    final PrintWriter pw = new PrintWriter(w);
    final DateFormat d = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
    pw.format("RequestAge Report for %s on %s\n", testName, d.format(new Date()));
    String sep = "";
    for (String h : headers) { pw.append(sep).append(h.replace(' ','_')); sep = "\t"; }
    pw.println();
    for (Stats stats : statsList) pw.format(format, reportRow(stats));
    return pw.toString();
  }
}
