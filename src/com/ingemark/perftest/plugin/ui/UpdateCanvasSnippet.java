package com.ingemark.perftest.plugin.ui;

import static java.lang.Math.sin;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.swt.layout.GridData.FILL;
import static org.eclipse.swt.layout.GridData.FILL_HORIZONTAL;

import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;

public class UpdateCanvasSnippet extends Composite
{
  static final Display display = new Display();
  static final ScheduledExecutorService sched = newSingleThreadScheduledExecutor();
  double phi, phase;
  boolean rectDrawn;
  Image drawable = new Image(display,1,1);
  long numbersLastPrinted;
  private Canvas canvas;

  public UpdateCanvasSnippet(Composite parent, int style) {
    super(parent, style);
    setLayout(new GridLayout(1, false));
    final Scale scale = new Scale(this, SWT.NONE);
    final GridData gd_scale = new GridData(FILL_HORIZONTAL);
    scale.setLayoutData(gd_scale);
    scale.setMinimum(100);
    scale.setMaximum(600);
    canvas = new Canvas(this, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED);
    gridData().align(FILL, FILL).grab(true, true).applyTo(canvas);
    sched.scheduleAtFixedRate(new Runnable() { public void run() {
      display.asyncExec(new Runnable() { public void run() {
        if (canvas.isDisposed()) return;
        phi = System.currentTimeMillis();
        canvas.redraw();
      }
      });}
    }, 0, 10, MILLISECONDS);
    scale.addSelectionListener(new SelectionAdapter() {
      @Override public void widgetSelected(SelectionEvent e) {
        phase = scale.getSelection();
        canvas.redraw();
      }
    });
    canvas.addPaintListener(new PaintListener() {
      public void paintControl(PaintEvent e) { draw(e.gc); } });
    canvas.addListener(SWT.Resize, new Listener() { public void handleEvent(Event event) {
      paintImage(canvas);
    }});
  }

  void paintImage(Canvas canvas) {
    drawable.dispose();
    drawable = new Image(display, canvas.getBounds().width, canvas.getBounds().height);
    final GC gc = new GC(drawable);
    gc.setForeground(display.getSystemColor(SWT.COLOR_BLUE));
    final Rectangle area = gc.getClipping();
    gc.drawRectangle(0, area.height - 51, 50, 50);
    gc.dispose();
  }

  void draw(GC gc) {
    if (drawable == null) paintImage(canvas);
    gc.drawImage(drawable, 0, 0);
    gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
    gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
    final Rectangle area = gc.getClipping();
    for (int x = 0; x < area.width; x++)
      gc.drawLine(x, 0, x, (int) (area.height / 2 - 30 * sin((phi / 20 - phase - x) / 10d)));
  }

  public static void main(String[] args) throws InterruptedException {
    final Shell shell = new Shell(display, SWT.SHELL_TRIM | SWT.DOUBLE_BUFFERED);
    shell.setLayout(new GridLayout(3, false));
    for (int i = 0; i < 9; i++) {
      final Composite c = new UpdateCanvasSnippet(shell, SWT.None);
      gridData().grab(true, true).applyTo(c);
    }
    shell.setSize(1000, 800);
    shell.open();
    while (!shell.isDisposed()) if (!display.readAndDispatch()) display.sleep();
    sched.shutdown();
    sched.awaitTermination(1, SECONDS);
    display.dispose();
  }

  static GridDataFactory gridData() {
    return GridDataFactory.fillDefaults();
  }

  @Override protected void checkSubclass() { }
}