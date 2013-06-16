package com.ingemark.perftest.script;

import static com.ingemark.perftest.Util.sneakyThrow;
import static com.ingemark.perftest.script.JsScope.JS_LOGGER_NAME;
import static org.jdom2.Namespace.getNamespace;
import static org.jdom2.filter.Filters.fpassthrough;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.StAXStreamBuilder;
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
  public static final String[] JS_METHODS = new String[] {
    "nsdecl", "ns", "xml", "parseXml", "xpath", "spy"
  };
  private static Logger jsLogger = getLogger(JS_LOGGER_NAME);
  private static final ReaderConfig readerCfg = new ReaderConfig();
  static { readerCfg.configureForSpeed(); }
  static final Map<String, Namespace> nsmap = new HashMap<>();
  static final Map<String, XPathExpression<Object>> xpathmap = new HashMap<>();
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
    if (name != null)
      return new JdomBuilder(name, cast(ns, Namespace.class));
    final Element el = cast(root, Element.class);
    return el != null? new JdomBuilder(el) : new JdomBuilder(cast(el, Document.class));
  }
  public static Document parseXml(Object _in) {
    _in = cast(_in, Object.class);
    try {
      return new StAXStreamBuilder().build(StreamReaderImpl.construct(
        _in instanceof Response?
           ByteSourceBootstrapper.construct(readerCfg, ((Response)_in).getResponseBodyAsStream())
           : CharSourceBootstrapper.construct(readerCfg, new StringReader((String)_in))));
    } catch (JDOMException | XMLStreamException | IOException e) { return sneakyThrow(e); }
  }
  public static XPathExpression xpath(String expr) {
    XPathExpression x = xpathmap.get(expr);
    if (x == null) {
      x = XPathFactory.instance().compile(expr, fpassthrough(), null, nsmap.values());
      xpathmap.put(expr, x);
    }
    return x;
  }
  public static <T> T spy(String msg, T ret) {
    jsLogger.debug("{}: {}", msg,
        ret instanceof NativeJavaObject? ((NativeJavaObject)ret).unwrap() : ret);
    return ret;
  }

  private static <T> T cast(Object o, Class<T> c) {
    return o == Undefined.instance? null
           : o instanceof NativeJavaObject? (T)((NativeJavaObject)o).unwrap()
           : (T)o;
  }
}
