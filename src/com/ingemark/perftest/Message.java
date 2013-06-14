package com.ingemark.perftest;

import java.io.Serializable;
import java.util.Arrays;

public class Message implements Serializable
{
  public static final int INTENSITY = 0, DIVISOR = 1, INIT = 2, STATS = 3, SHUTDOWN = 4, ERROR = 5,
      EXCEPTION = 6;
  final int type;
  final Serializable value;
  public Message(int type, Serializable value) { this.type = type; this.value = value; }
  public String toString() {
    return String.format("Msg %d %s", type,
        value.getClass().isArray()? Arrays.toString((Object[])value) : value);
  }
}
