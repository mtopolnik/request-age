package com.ingemark.requestage.script;

import static com.ingemark.requestage.Util.sneakyThrow;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

public class UrlBuilder
{
  private final NativeJavaObject wrapper;
  private final StringBuilder b = new StringBuilder(16);
  private boolean withinQuery;

  private UrlBuilder(Scriptable scope, String urlBase) {
    wrapper = new NativeJavaObject(scope, this, getClass());
    wrapper.setPrototype(new NativeObject());
    b.append(urlBase);
  }

  static Scriptable urlBuilder(Scriptable scope, String urlBase) {
    return new UrlBuilder(scope, urlBase).wrapper;
  }

  public Scriptable s(Object... segs) {
    if (withinQuery)
      throw new IllegalStateException("Cannot add path segments after query params");
    for (Object seg : segs) {
      if (b.charAt(b.length()-1) != '/') b.append('/');
      if (seg != null) b.append(seg);
    }
    return wrapper;
  }
  public Scriptable q(Object... paramValuePairs) {
    for (int i = 0; i < paramValuePairs.length;) {
      b.append(withinQuery? '&' : '?');
      withinQuery = true;
      b.append(encode(paramValuePairs[i++].toString()))
       .append('=')
       .append(encode(paramValuePairs[i++].toString()));
    }
    return wrapper;
  }
  @Override public String toString() { return b.toString(); }

  static String encode(String s) {
    try { return URLEncoder.encode(s, "UTF-8"); }
    catch (UnsupportedEncodingException e) { return sneakyThrow(e); }
  }
}
