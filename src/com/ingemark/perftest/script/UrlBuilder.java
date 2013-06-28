package com.ingemark.perftest.script;

import static com.ingemark.perftest.Util.sneakyThrow;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class UrlBuilder {
  private final StringBuilder b = new StringBuilder(16);
  private boolean withinQuery;

  UrlBuilder(Object urlBase) { b.append(urlBase); }

  public UrlBuilder s(Object... segs) {
    if (withinQuery)
      throw new IllegalStateException("Cannot add path segments after query params");
    for (Object seg : segs) {
      if (b.charAt(b.length()-1) != '/') b.append('/');
      if (seg != null) b.append(seg);
    }
    return this;
  }
  public UrlBuilder q(Object... paramValuePairs) {
    for (int i = 0; i < paramValuePairs.length;) {
      b.append(withinQuery? '&' : '?');
      withinQuery = true;
      b.append(encode(paramValuePairs[i++].toString()))
       .append('=')
       .append(encode(paramValuePairs[i++].toString()));
    }
    return this;
  }
  @Override public String toString() { return b.toString(); }

  static String encode(String s) {
    try { return URLEncoder.encode(s, "UTF-8"); }
    catch (UnsupportedEncodingException e) { return sneakyThrow(e); }
  }
}
