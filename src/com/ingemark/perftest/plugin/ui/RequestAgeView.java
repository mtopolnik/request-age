package com.ingemark.perftest.plugin.ui;

import static com.ingemark.perftest.Message.EXCEPTION;
import static com.ingemark.perftest.Util.gridData;
import static com.ingemark.perftest.Util.sneakyThrow;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_ERROR;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_INIT_HIST;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_RUN_SCRIPT;
import static com.ingemark.perftest.plugin.StressTestActivator.STATS_EVTYPE_BASE;
import static com.ingemark.perftest.plugin.StressTestActivator.stressTestPlugin;
import static org.eclipse.debug.core.DebugPlugin.newProcess;
import static org.eclipse.debug.core.ILaunchManager.RUN_MODE;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.List;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.Launch;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;

import com.ingemark.perftest.DialogInfo;
import com.ingemark.perftest.IStressTestServer;
import com.ingemark.perftest.Message;
import com.ingemark.perftest.Stats;
import com.ingemark.perftest.StressTestServer;
import com.ingemark.perftest.StressTester;

public class RequestAgeView extends ViewPart
{
  static final Logger log = getLogger(RequestAgeView.class);
  private static final int MIN_THROTTLE = 50;
  public static RequestAgeView instance;
  public Composite statsParent;
  private Process subprocess;
  private Scale throttle;
  private IStressTestServer testServer = StressTestServer.NULL;
  private Action stopAction;

  public void createPartControl(final Composite p) {
    instance = this;
    p.setLayout(new GridLayout(2, false));
    stopAction = new Action() {
      final ImageDescriptor img = stressTestPlugin().imageDescriptor("stop.gif");
      @Override public ImageDescriptor getImageDescriptor() { return img; }
      @Override public void run() { shutdown(p); }
    };
    stopAction.setEnabled(false);
    getViewSite().getActionBars().getToolBarManager().add(stopAction);
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
      public void handleEvent(final Event event) {
        try {
          shutdown(p);
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
                histogram.canvas.addMouseListener(new MouseListener() {
                  @Override public void mouseDoubleClick(MouseEvent e) {
                    testServer.send(new Message(EXCEPTION, histogram.stats.name));
                  }
                  @Override public void mouseUp(MouseEvent e) {}
                  @Override public void mouseDown(MouseEvent e) {}
                });
              }
              p.layout(true);
              p.notifyListeners(SWT.Paint, new Event());
          }});
          statsParent.addListener(EVT_ERROR, new Listener() {
            @Override public void handleEvent(Event e) {
              final Throwable t = ((Throwable)e.data);
              InfoDialog.show(new DialogInfo("Stress testing error", t));
            }
          });
          testServer = new StressTestServer(statsParent);
          subprocess = StressTester.launchTester((String)event.data);
          try {
            final Launch launch = new Launch(null, RUN_MODE, null);
            newProcess(launch, subprocess, "Stress Test");
            DebugPlugin.getDefault().getLaunchManager().addLaunch(launch);
          }
          catch (Exception e) { sneakyThrow(e); }
          stopAction.setEnabled(true);
        }
        catch (Throwable t) {
          InfoDialog.show(new DialogInfo("Stress test init error", t));
        }
      }});
  }

  void shutdown(Composite parent) {
    try {
      testServer.shutdown();
      testServer = StressTestServer.NULL;
      if (subprocess != null) {
        subprocess.destroy();
        subprocess.waitFor();
      }
      statsParent.dispose();
      if (parent != null) newStatsParent(parent);
      stopAction.setEnabled(false);
    } catch (InterruptedException e) { sneakyThrow(e); }
  }

  static String joinPath(String[] ps) {
    final StringBuilder b = new StringBuilder(128);
    for (String p : ps) b.append(p).append(":");
    return b.toString();
  }

  static int pow(int in) { return (int)Math.pow(10, in/100d); }
  @Override public void dispose() { shutdown(null); }

  @Override public void setFocus() { throttle.setFocus(); }

  private void applyThrottle() {
    testServer.intensity(pow(throttle.getSelection()));
  }
}