package com.ingemark.perftest;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

public class Util
{
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
  public static void nettySend(Channel channel, Message msg, boolean sync) {
    if (channel == null) {
      System.err.println("Attempt to send message without connected client: " + msg);
      return;
    }
    final ChannelFuture fut = channel.write(msg);
    fut.addListener(new ChannelFutureListener() {
      @Override public void operationComplete(ChannelFuture f) {
        if (f.isSuccess()) return;
        System.err.println("Failed to send message");
        f.getCause().printStackTrace();
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
}
