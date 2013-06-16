package com.ingemark.perftest;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

public class DialogInfo implements Serializable {
  public final String title, msg;

  public DialogInfo(LiveStats ls) {
    this.title = ls.name + " - Last Reported Exception";
    this.msg = excToString(ls.lastException);
  }
  public DialogInfo(String title, Throwable exc) {
    this.title = title; this.msg = excToString(exc);
  }

  private static String excToString(Throwable t) {
    if (t == null) return "";
    final StringWriter sw = new StringWriter(256);
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
