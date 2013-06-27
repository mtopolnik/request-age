package com.ingemark.perftest.script;

import static com.ingemark.perftest.Util.sneakyThrow;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class UrlBuilder {
  private final StringBuilder b = new StringBuilder(16);
  private boolean withinQuery;

  UrlBuilder(String urlBase) { b.append(urlBase); }

  public UrlBuilder s(String seg) {
    if (withinQuery)
      throw new IllegalStateException("Cannot add path segments after query params");
    if (b.charAt(b.length()-1) != '/') b.append('/');
    if (seg != null) b.append(seg);
    return this;
  }
  public UrlBuilder q(String param, String val) {
    b.append(withinQuery? '&' : '?');
    b.append(encode(param)).append('=').append(encode(val));
    withinQuery = true;
    return this;
  }
  @Override public String toString() { return b.toString(); }

  static String encode(String s) {
    try { return URLEncoder.encode(s, "UTF-8"); }
    catch (UnsupportedEncodingException e) { return sneakyThrow(e); }
  }
}
