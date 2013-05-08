package com.ingemark.perftest.plugin.ui;

import static org.eclipse.jface.dialogs.MessageDialog.openError;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.ui.part.ViewPart;

import com.ingemark.perftest.IStressTester;
import com.ingemark.perftest.RequestProvider;
import com.ingemark.perftest.Script;
import com.ingemark.perftest.Stats;
import com.ingemark.perftest.StressTester;
import com.ingemark.perftest.script.Parser;

public class RequestAgeView extends ViewPart
{
  public static final int STATS_EVTYPE_BASE = 1024;
  Scale throttle;
  Display disp;
  IStressTester stressTester = StressTester.NULL;
  Stats stats = new Stats();

  public void createPartControl(Composite p) {
    disp = Display.getDefault();
    final GridLayout l = new GridLayout(2, false);
    p.setLayout(l);
    throttle = new Scale(p, SWT.VERTICAL);
    throttle.setMinimum(70);
    throttle.setMaximum(330);
    final Composite statsParent = new Composite(p, SWT.NONE);
    gridData().grab(true, true).applyTo(statsParent);
    statsParent.setLayout(new GridLayout(2, true));
    final Script script = new Parser(getClass().getResourceAsStream("/test1.sts")).parse();
    for (RequestProvider rp : script.testReqs) {
      final HistogramViewer histogram = new HistogramViewer(statsParent);
      gridData().grab(true, true).applyTo(histogram.canvas);
      p.addListener(RequestAgeView.STATS_EVTYPE_BASE + rp.liveStats.index, new Listener() {
        public void handleEvent(Event event) {
          histogram.statsUpdate((Stats) event.data);
        }
      });
    }
    stressTester = new StressTester(p, script);
    throttle.addSelectionListener(new SelectionAdapter() {
      @Override public void widgetSelected(SelectionEvent e) {
        stressTester.setIntensity(pow(throttle.getSelection()));
    }});
    try { stressTester.runTest(); }
    catch (Throwable t) {
      openError(null, "Stress test init error", String.format(
          "%s: %s", t.getClass().getSimpleName(), t.getMessage()));
    }
  }

  static int pow(int in) { return (int)Math.pow(10, in/100d); }
  static GridDataFactory gridData() { return GridDataFactory.fillDefaults(); }

  @Override public void dispose() { stressTester.shutdown(); }
  @Override public void setFocus() { }
}