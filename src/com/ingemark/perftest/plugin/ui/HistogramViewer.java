package com.ingemark.perftest.plugin.ui;

import static com.ingemark.perftest.StressTester.HIST_SIZE;
import static com.ingemark.perftest.StressTester.TIMESLOTS_PER_SEC;
import static java.lang.Math.log10;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.ingemark.perftest.Stats;

public class HistogramViewer implements PaintListener
{
  static final int
    HIST_HEIGHT_SCALE = 1, HIST_BAR_WIDTH = 2, HIST_XOFFSET = 50, METER_SCALE = 50,
    TOTAL_REQS_OFFSET = HIST_XOFFSET + HIST_SIZE*HIST_BAR_WIDTH;
  static int HIST_YOFFSET;
  Stats stats = new Stats();
  final Canvas canvas;

  HistogramViewer(Composite parent) {
    canvas = new Canvas(parent, SWT.NONE);
    canvas.setBackground(color(SWT.COLOR_WHITE));
    canvas.addPaintListener(this);
    final GC gc = new GC(canvas);
    try {HIST_YOFFSET = 10 + gc.getFontMetrics().getHeight();} finally {gc.dispose();}
  }

  void statsUpdate(Stats stats) {
    this.stats = stats;
    final GC gc = new GC(canvas);
    try {drawStats(gc);} finally {gc.dispose();}
  }

  @Override public void paintControl(PaintEvent e) {
    final GC gc = e.gc;
    paintRect(gc, SWT.COLOR_WHITE, 0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    drawStaticStuff(gc, 15);
    drawStats(gc);
  }

  void drawStats(GC gc) {
    paintMeterBars(gc);
    paintHistogram(gc);
    paintTotalReqs(gc);
    paintChartName(gc);
  }

  void paintMeterBars(GC gc) {
    paintBar(gc, SWT.COLOR_WHITE, 8, 0, 5, 0);
    int a = toMeter(stats.reqsPerSec), b = toMeter(stats.succRespPerSec + stats.failsPerSec);
    paintBar(gc, SWT.COLOR_DARK_BLUE, 0, 0, 5, a);
    final int color;
    if (b < a) {
      int tmp = a; a = b; b = tmp;
      color = SWT.COLOR_RED;
    } else color = SWT.COLOR_DARK_GREEN;
    paintBarNoBg(gc, color, 8, a, 5, b-a);
    paintBarNoBg(gc, SWT.COLOR_RED, 8, 0, 5, toMeter(stats.failsPerSec));
  }

  void paintHistogram(GC gc) {
    final int[] hist = stats.histogram;
    int i;
    for (i = 0; i < hist.length; i++) histBar(gc, SWT.COLOR_BLUE, i, 1, hist[i]);
    histBar(gc, SWT.COLOR_BLUE, i, (HIST_SIZE-i), 0);
  }

  void paintTotalReqs(GC gc) {
    final int
    maxTotalBars = 1 +
    (canvas.getClientArea().width - HIST_XOFFSET - HIST_SIZE*HIST_BAR_WIDTH) / HIST_BAR_WIDTH,
    fullTotalBarHeight = 100,
    fullTotalBars = min(stats.pendingReqs/fullTotalBarHeight, maxTotalBars),
    lastTotalBarHeight = stats.pendingReqs%fullTotalBarHeight;
    final int totalReqColor = SWT.COLOR_GRAY;
    histBar(gc, totalReqColor, HIST_SIZE, fullTotalBars, fullTotalBarHeight);
    histBar(gc, totalReqColor, HIST_SIZE+ fullTotalBars, 1, lastTotalBarHeight);
    histBar(gc, totalReqColor, HIST_SIZE+ fullTotalBars+ 1, maxTotalBars-fullTotalBars-1, 0);
    final String s = String.valueOf(stats.pendingReqs);
    final int totalReqsCenter = TOTAL_REQS_OFFSET + ((fullTotalBars+1)*HIST_BAR_WIDTH) / 2;
    final Point ext = gc.stringExtent(s);
    final int labelBase = HIST_YOFFSET+fullTotalBarHeight+5;
    paintRect(gc, SWT.COLOR_WHITE,
        TOTAL_REQS_OFFSET, labelBase, maxTotalBars*HIST_BAR_WIDTH, ext.y);
    drawString(gc, s, max(TOTAL_REQS_OFFSET, totalReqsCenter - ext.x/2), labelBase);
  }

  void paintChartName(GC gc) {
    final Point ext = gc.stringExtent(stats.name);
    drawString(gc, stats.name,
        HIST_XOFFSET + max((HIST_SIZE*HIST_BAR_WIDTH-ext.x)/2, 0), 5 + tickMarkY(3, 2), true);
  }

  void drawStaticStuff(GC gc, int xOffset) {
    final FontMetrics m = gc.getFontMetrics();
    final int labelOffset = m.getAscent()/2 - 1;
    loop: for (int exp = 0;; exp++)
      for (int i = 2; i <= 10; i += 2) {
        final int label = (int)pow(10, exp)*i;
        if (label >= 3000) break loop;
        final int y = tickMarkY(exp, i);
        drawHorLine(gc, xOffset, y, 5);
        if (i == 2 || i == 10)
          drawString(gc, String.valueOf(label), 8+xOffset, y-labelOffset);
      }
    for (int i = 0;; i++) {
      final int barIndex = i*TIMESLOTS_PER_SEC;
      final int xPos = HIST_XOFFSET + barIndex*HIST_BAR_WIDTH;
      drawVerLine(gc, xPos, m.getHeight()+3, 5);
      final String label = String.valueOf(i);
      final Point ext = gc.stringExtent(label);
      drawString(gc, label, xPos - ext.x/2, 2);
      if (barIndex >= HIST_SIZE) break;
    }
  }

  void drawString(GC gc, String s, int x, int y) {
    drawString(gc, s, x, y, false);
  }
  void drawString(GC gc, String s, int x, int y, boolean fitHeight) {
    gc.setForeground(color(SWT.COLOR_BLACK));
    gc.setBackground(color(SWT.COLOR_WHITE));
    final FontMetrics m = gc.getFontMetrics();
    y = canvas.getClientArea().height-m.getLeading()-m.getAscent()-y;
    if (fitHeight) y = max(y, 0);
    gc.drawString(s, x, y);
  }
  void drawHorLine(GC gc, int x, int y, int len) {
    gc.setForeground(color(SWT.COLOR_BLACK));
    final int height = canvas.getClientArea().height;
    gc.drawLine(x, height-y, x+len, height-y);
  }
  void drawVerLine(GC gc, int x, int y, int len) {
    gc.setForeground(color(SWT.COLOR_BLACK));
    final int height = canvas.getClientArea().height;
    gc.drawLine(x, height-y, x, height-y-len);
  }
  void paintRect(GC gc, int color, int x, int y, int width, int height) {
    final Rectangle all = canvas.getClientArea();
    gc.setBackground(color(color));
    height = min(height, all.height-y);
    gc.fillRectangle(x, max(0, all.height-y-height), width, height);
  }
  void paintBarNoBg(GC gc, int color, int x, int y, int width, int height) {
    paintRect(gc, color, x, y, width, height);
  }
  void paintBar(GC gc, int color, int x, int y, int width, int height) {
    paintBarNoBg(gc, color, x, y, width, height);
    paintRect(gc, SWT.COLOR_WHITE, x, y+height, width, Integer.MAX_VALUE);
  }
  void histBar(GC gc, int color, int pos, int barCount, int barHeight) {
    paintBar(gc, color, HIST_XOFFSET+pos*HIST_BAR_WIDTH, HIST_YOFFSET,
        barCount*HIST_BAR_WIDTH, barHeight*HIST_HEIGHT_SCALE);
  }
  Color color(int id) { return Display.getCurrent().getSystemColor(id); }

  static int toMeter(int in) { return (int) (METER_SCALE * max(0d, log10(in))); }

  static int tickMarkY(int exp, int i) { return (int) (METER_SCALE * (exp + log10(i))); }
}
