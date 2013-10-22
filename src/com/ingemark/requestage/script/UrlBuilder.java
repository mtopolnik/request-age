package com.ingemark.requestage.script;

import static com.ingemark.requestage.Util.builderWrapper;
import static com.ingemark.requestage.Util.sneakyThrow;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

public class UrlBuilder
{
  private static final Pattern urlBaseRegex = Pattern.compile("(.+?)://(.+?)(/.*?)?(?:\\?(.*))?");
  private final StringBuilder
    pathBuilder = new StringBuilder(16),
    qparamsBuilder = new StringBuilder(16);
  private final String scheme, authority;
  private String fragment;

  private UrlBuilder(String urlBase) {
    final Matcher m = urlBaseRegex.matcher(urlBase);
    if (!m.matches())
      throw ScriptRuntime.constructError("IllegalUrl", "The URL " + urlBase + " is invalid");
    scheme = m.group(1); authority = m.group(2);
    if (m.group(3) != null) pathBuilder.append(m.group(3));
    if (m.group(4) != null) qparamsBuilder.append(m.group(4));
  }

  static Scriptable urlBuilder(Scriptable scope, String urlBase) {
    return builderWrapper(scope, new UrlBuilder(urlBase));
  }

  public UrlBuilder s(Object... segs) {
    for (Object seg : segs) {
      if (pathBuilder.length() == 0 ||
          pathBuilder.charAt(pathBuilder.length()-1) != '/')
        pathBuilder.append('/');
      if (seg != null) {
        if (seg instanceof Collection) {
          final Collection colseg = (Collection)seg;
          s(colseg.toArray(new Object[colseg.size()]));
        }
        else pathBuilder.append(seg);
      }
    }
    return this;
  }
  public UrlBuilder pp(Object... pps) {
    for (int i = 0; i < pps.length;) {
      final Object p = pps[i++], v;
      if (p instanceof List) {
        final List l = (List)p;
        q(l.toArray(new Object[l.size()]));
        continue;
      }
      v = pps[i++];
      pathBuilder.append(';').append(p.toString()).append('=').append(v.toString());
    }
    return this;
  }
  public UrlBuilder q(Object... qps) {
    for (int i = 0; i < qps.length;) {
      final Object p = qps[i++], v;
      if (p instanceof List) {
        final List l = (List)p;
        q(l.toArray(new Object[l.size()]));
        continue;
      }
      v = qps[i++];
      if (qparamsBuilder.length() > 0) qparamsBuilder.append('&');
      qparamsBuilder.append(p).append('=').append(v);
    }
    return this;
  }
  public UrlBuilder frag(Object frag) {
    fragment = frag != null? frag.toString() : null;
    return this;
  }

  @Override public String toString() {
    try {
      return new URI(scheme, authority, pathBuilder.toString(), qparamsBuilder.toString(), fragment)
        .toASCIIString();
    } catch (URISyntaxException e) { return sneakyThrow(e); }
  }
}
