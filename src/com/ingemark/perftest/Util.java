package com.ingemark.perftest;

public class Util
{
  public static int toIndex(int[] array, int timeSlot) {
    int slot = timeSlot%array.length;
    return slot >= 0? slot : slot + array.length;
  }
  public static int arraySum(int[] array) {
    int sum = 0;
    for (int cnt : array) sum += cnt;
    return sum;
  }
  public static long now() { return System.nanoTime(); }
}
