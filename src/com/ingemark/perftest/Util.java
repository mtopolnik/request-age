package com.ingemark.perftest;

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
}
