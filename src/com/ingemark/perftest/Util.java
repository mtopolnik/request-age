package com.ingemark.perftest;

import java.io.Serializable;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingemark.perftest.plugin.ui.RequestAgeView;

public class Util
{
  static final Logger log = LoggerFactory.getLogger(Util.class);
  public static int toIndex(int[] array, int timeSlot) {
    return toIndex(array.length, timeSlot);
  }
  public static int toIndex(char[] array, int timeSlot) {
    return toIndex(array.length, timeSlot);
  }
  private static int toIndex(int length, int timeSlot) {
    int slot = timeSlot%length;
    return slot >= 0? slot : slot + length;
  }
  public static int arraySum(int[] array) {
    int sum = 0;
    for (int cnt : array) sum += cnt;
    return sum;
  }
  public static long now() { return System.nanoTime(); }

  public static String join(String separator, String... parts) {
    final StringBuilder b = new StringBuilder(128);
    String sep = "";
    for (String part : parts) { b.append(sep).append(part); sep = separator; }
    return b.toString();
  }
  public static void nettySend(Channel channel, final Message msg, boolean sync) {
    if (channel == null) {
      log.error("Attempt to send message without connected peer: " + msg);
      return;
    }
    final ChannelFuture fut = channel.write(msg);
    fut.addListener(new ChannelFutureListener() {
      @Override public void operationComplete(ChannelFuture f) {
        if (!f.isSuccess()) log.error("Failed to send message {}", msg, f.getCause());
      }
    });
    if (sync)
      try { fut.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
  }
  public static void nettySend(Channel channel, Message msg) {
    nettySend(channel, msg, false);
  }
  public static <T> T spy(String msg, T x) {
    System.out.println(msg + ": " + x); return x;
  }
  static void swtSend(final int evtype, final Serializable value) {
    Display.getDefault().asyncExec(new Runnable() { public void run() {
      final Event e = new Event();
      e.data = value;
      RequestAgeView.instance.statsParent.notifyListeners(evtype, e);
    }});
  }
}
