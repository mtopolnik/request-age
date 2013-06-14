package com.ingemark.perftest;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

public class ExceptionInfo implements Serializable {
  public final String title;
  public final Throwable exc;

  public ExceptionInfo(LiveStats ls) {
    this.title = ls.name + " - Last Reported Exception";
    this.exc = ls.lastException;
  }
  public ExceptionInfo(String title, Throwable exc) {
    this.title = title; this.exc = exc;
  }

  public String excToString() {
    if (exc == null) return "";
    final StringWriter sw = new StringWriter(256);
    exc.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
