package com.ingemark.perftest.script;

import static com.ingemark.perftest.Util.sneakyThrow;
import static com.ingemark.perftest.script.JsScope.JS_LOGGER_NAME;
import static java.util.Arrays.asList;
import static org.jdom2.Namespace.getNamespace;
import static org.jdom2.filter.Filters.fpassthrough;
import static org.jdom2.output.Format.getPrettyFormat;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.StAXStreamBuilder;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;

import com.fasterxml.aalto.in.ByteSourceBootstrapper;
import com.fasterxml.aalto.in.CharSourceBootstrapper;
import com.fasterxml.aalto.in.ReaderConfig;
import com.fasterxml.aalto.stax.StreamReaderImpl;
import com.ning.http.client.Response;

public class JsFunctions {
  private static final int COMPILED_EXPR_CACHE_LIMIT = 256;
  public static final String[] JS_METHODS = new String[] {
    "nsdecl", "ns", "xml", "parseXml", "prettyXml", "xpath", "regex", "spy"
  };
  private static Logger jsLogger = getLogger(JS_LOGGER_NAME);
  private static final ReaderConfig readerCfg = new ReaderConfig();
  static { readerCfg.configureForSpeed(); }
  private static final Map<String, Namespace> nsmap = concHashMap();
  private static final Map<String, XPathExpression<Object>> xpathmap = concHashMap();
  private static final Map<String, Pattern> regexmap = concHashMap();

  public static Namespace nsdecl(String prefix, String url) {
    final Namespace ns = ns(prefix, url);
    nsmap.put(prefix, ns);
    return ns;
  }
  public static Namespace ns(String prefix, Object _uri ) {
    final String uri = cast(_uri, String.class);
    return uri != null? getNamespace(prefix, uri) : nsmap.get(prefix);
  }
  public static JdomBuilder xml(Object root, Object ns) {
    final String name = cast(root, String.class);
    if (name != null) return new JdomBuilder(name, cast(ns, Namespace.class));
    final Element el = cast(root, Element.class);
    return el != null? new JdomBuilder(el) : new JdomBuilder(cast(el, Document.class));
  }
  public static Document parseXml(Object in) {
    try {
      final Response r = cast(in, Response.class);
      final InputStream is = r != null? r.getResponseBodyAsStream() : cast(in, InputStream.class);
      final String s = is == null? cast(in, String.class) : null;
      if (is == null && s == null) throw new IllegalArgumentException("Got " + in +
          "; supporting HTTP Response, InputStream, or String");
      return new StAXStreamBuilder().build(StreamReaderImpl.construct(is != null?
          ByteSourceBootstrapper.construct(readerCfg, is)
          : CharSourceBootstrapper.construct(readerCfg, new StringReader(s))));
    } catch (Exception e) { return sneakyThrow(e); }
  }
  public static Object prettyXml(Object input) {
    final Object o = cast(input, Object.class);
    if (o == null) return "<!--undefined-->";
    if (!(o instanceof Response) && !(o instanceof InputStream) &&
        !(o instanceof Document) && !(o instanceof Content))
      throw new IllegalArgumentException(
          "Got " + o.getClass().getName() + ". Supporting HTTP Response, " +
      		"InputStream, or JDOM2 Document/Content");
    return new Object() { @Override public String toString() {
      final Object in = o instanceof Response || o instanceof InputStream? parseXml(o) : o;
      final XMLOutputter xo = new XMLOutputter(getPrettyFormat());
      final StringWriter w = new StringWriter(256);
      try {
        if (in instanceof Document) xo.output((Document)in, w);
        else xo.output(asList((Content)in), w);
      } catch (IOException e) { return sneakyThrow(e); }
      return w.toString();
    }};
  }
  public static XPathExpression xpath(String expr) {
    XPathExpression x = xpathmap.get(expr);
    if (x == null) {
      x = XPathFactory.instance().compile(expr, fpassthrough(), null, nsmap.values());
      if (xpathmap.size() < COMPILED_EXPR_CACHE_LIMIT) xpathmap.put(expr, x);
    }
    return x;
  }
  public static Pattern regex(String regex) {
    Pattern p = regexmap.get(regex);
    if (p == null) {
      p = Pattern.compile(regex);
      if (regexmap.size() < COMPILED_EXPR_CACHE_LIMIT) regexmap.put(regex, p);
    }
    return p;
  }
  public static <T> T spy(String msg, T ret) {
    jsLogger.debug("{}: {}", msg,
        ret instanceof NativeJavaObject? ((NativeJavaObject)ret).unwrap() : ret);
    return ret;
  }

  private static <T> T cast(Object o, Class<T> c) {
    if (o == Undefined.instance) return null;
    if (o instanceof NativeJavaObject) o = ((NativeJavaObject)o).unwrap();
    return c.isInstance(o)? (T) o : null;
  }

  private static <K,V> Map<K,V> concHashMap() { return new ConcurrentHashMap<K,V>(); }
}
