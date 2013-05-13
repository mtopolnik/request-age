package com.ingemark.perftest.plugin.ui;

import static com.ingemark.perftest.plugin.StressTestActivator.INIT_HIST_EVTYPE;
import static com.ingemark.perftest.plugin.StressTestActivator.RUN_SCRIPT_EVTYPE;
import static com.ingemark.perftest.plugin.StressTestActivator.STATS_EVTYPE_BASE;
import static com.ingemark.perftest.plugin.StressTestActivator.stressTestPlugin;
import static org.eclipse.core.runtime.FileLocator.getBundleFile;
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

import com.ingemark.perftest.IStressTestServer;
import com.ingemark.perftest.Stats;
import com.ingemark.perftest.StressTestServer;
import com.ingemark.perftest.StressTester;

public class RequestAgeView extends ViewPart
{
  private static final int MIN_THROTTLE = 70;
  public static RequestAgeView instance;
  public Composite statsParent;
  Process subprocess;
  Scale throttle;
  Display disp;
  IStressTestServer testServer = StressTestServer.NULL;
  Stats stats = new Stats();


  public void createPartControl(final Composite p) {
    instance = this;
    disp = Display.getDefault();
    final GridLayout l = new GridLayout(2, false);
    p.setLayout(l);
    throttle = new Scale(p, SWT.VERTICAL);
    throttle.setMinimum(MIN_THROTTLE);
    throttle.setMaximum(330);
    throttle.addSelectionListener(new SelectionAdapter() {
      @Override public void widgetSelected(SelectionEvent e) { applyThrottle(); }
    });
    newStatsParent(p);
  }

  void newStatsParent(final Composite p) {
    statsParent = new Composite(p, SWT.NONE);
    gridData().grab(true, true).applyTo(statsParent);
    statsParent.setLayout(new GridLayout(2, true));
    statsParent.addListener(RUN_SCRIPT_EVTYPE, new Listener() {
      public void handleEvent(Event event) {
        try {
          testServer.shutdown();
          if (subprocess != null) subprocess.waitFor();
          statsParent.dispose();
          newStatsParent(p);
          statsParent.addListener(INIT_HIST_EVTYPE, new Listener() {
            @Override public void handleEvent(Event event) {
              System.out.println("Init histogram");
              throttle.setSelection(MIN_THROTTLE);
              applyThrottle();
              for (int i = 0; i < (int)event.data; i++) {
                final HistogramViewer histogram = new HistogramViewer(statsParent);
                gridData().grab(true, true).applyTo(histogram.canvas);
                statsParent.addListener(STATS_EVTYPE_BASE + i, new Listener() {
                  public void handleEvent(Event event) { histogram.statsUpdate((Stats) event.data); }
                });
              }
              p.layout(true);
          }});
          System.out.println("Start test server");
          testServer = new StressTestServer(statsParent);
          System.out.println("Start subprocess");
          final String cp = getBundleFile(stressTestPlugin().bundle()).getCanonicalPath();
          System.out.println("cp " + cp);
          subprocess = new ProcessBuilder("java", "-Xmx64m", "-XX:+UseConcMarkSweepGC",
              "-cp", String.format("%s:%s/bin:%s/lib", cp, cp, cp),
              StressTester.class.getName(), (String) event.data) .inheritIO().start();
        }
        catch (Throwable t) {
          openError(null, "Stress test init error", String.format(
              "%s: %s", t.getClass().getSimpleName(), t.getMessage()));
        }
      }});
  }

  static String joinPath(String[] ps) {
    final StringBuilder b = new StringBuilder(128);
    for (String p : ps) b.append(p).append(":");
    return b.toString();
  }

  static int pow(int in) { return (int)Math.pow(10, in/100d); }
  static GridDataFactory gridData() { return GridDataFactory.fillDefaults(); }

  @Override public void dispose() { testServer.shutdown(); }
  @Override public void setFocus() { }

  private void applyThrottle() {
    testServer.intensity(pow(throttle.getSelection()));
  }
}