package com.ingemark.perftest.plugin.ui;

import static com.ingemark.perftest.Util.sneakyThrow;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_ERROR;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_INIT_HIST;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_RUN_SCRIPT;
import static com.ingemark.perftest.plugin.StressTestActivator.STATS_EVTYPE_BASE;
import static org.eclipse.jface.dialogs.MessageDialog.openError;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.List;

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
import org.slf4j.Logger;

import com.ingemark.perftest.IStressTestServer;
import com.ingemark.perftest.Stats;
import com.ingemark.perftest.StressTestServer;
import com.ingemark.perftest.StressTester;

public class RequestAgeView extends ViewPart
{
  static final Logger log = getLogger(RequestAgeView.class);
  private static final int MIN_THROTTLE = 50;
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
    p.setLayout(new GridLayout(2, false));
    throttle = new Scale(p, SWT.VERTICAL);
    throttle.setMinimum(MIN_THROTTLE);
    throttle.setMaximum(400);
    throttle.addSelectionListener(new SelectionAdapter() {
      @Override public void widgetSelected(SelectionEvent e) { applyThrottle(); }
    });
    newStatsParent(p);
  }

  void newStatsParent(final Composite p) {
    statsParent = new Composite(p, SWT.NONE);
    gridData().grab(true, true).applyTo(statsParent);
    statsParent.setLayout(new GridLayout(2, false));
    statsParent.addListener(EVT_RUN_SCRIPT, new Listener() {
      public void handleEvent(Event event) {
        try {
          shutdown();
          statsParent.dispose();
          newStatsParent(p);
          statsParent.addListener(EVT_INIT_HIST, new Listener() {
            @Override public void handleEvent(Event event) {
              log.debug("Init histogram");
              throttle.setSelection(MIN_THROTTLE);
              applyThrottle();
              final List<Integer> indices = (List<Integer>)event.data;
              Collections.sort(indices);
              for (int i : indices) {
                final HistogramViewer histogram = new HistogramViewer(statsParent);
                gridData().grab(true, true).applyTo(histogram.canvas);
                statsParent.addListener(STATS_EVTYPE_BASE + i, new Listener() {
                  public void handleEvent(Event e) { histogram.statsUpdate((Stats) e.data); }
                });
              }
              p.layout(true);
              p.notifyListeners(SWT.Paint, new Event());
          }});
          statsParent.addListener(EVT_ERROR, new Listener() {
            @Override public void handleEvent(Event e) {
              final Throwable t = ((Throwable)e.data);
              openError(statsParent.getShell(), "Stress testing error",
                  t.getClass().getSimpleName() + ": " + t.getMessage());
            }
          });
          testServer = new StressTestServer(statsParent);
          subprocess = StressTester.launchTester((String)event.data);
        }
        catch (Throwable t) {
          openError(null, "Stress test init error", String.format(
              "%s: %s", t.getClass().getSimpleName(), t.getMessage()));
        }
      }});
  }

  void shutdown() {
    try {
      testServer.shutdown();
      if (subprocess != null) {
        subprocess.destroy();
        subprocess.waitFor();
      }
    } catch (InterruptedException e) { sneakyThrow(e); }
  }

  static String joinPath(String[] ps) {
    final StringBuilder b = new StringBuilder(128);
    for (String p : ps) b.append(p).append(":");
    return b.toString();
  }

  static int pow(int in) { return (int)Math.pow(10, in/100d); }
  static GridDataFactory gridData() { return GridDataFactory.fillDefaults(); }

  @Override public void dispose() { shutdown(); }

  @Override public void setFocus() { throttle.setFocus(); }

  private void applyThrottle() {
    testServer.intensity(pow(throttle.getSelection()));
  }
}