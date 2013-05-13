package com.ingemark.perftest;

public class Message
{
  static final int INTENSITY = 0, DIVISOR = 1, INIT = 2, STATS = 3, SHUTDOWN = 4;
  final int type;
  final Object value;
  public Message(int type, int value) { this.type = type; this.value = value; }
  public Message(int type, Stats value) { this.type = type; this.value = value; }
  public String toString() { return String.format("Msg %d %d", type, value); }
}
