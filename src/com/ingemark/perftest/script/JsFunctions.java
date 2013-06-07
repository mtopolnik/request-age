package com.ingemark.perftest.script;

import static com.ingemark.perftest.Util.sneakyThrow;
import static org.jdom2.Namespace.getNamespace;

import java.io.StringReader;

import javax.xml.stream.XMLStreamException;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.StAXStreamBuilder;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Undefined;

import com.fasterxml.aalto.in.CharSourceBootstrapper;
import com.fasterxml.aalto.in.ReaderConfig;
import com.fasterxml.aalto.stax.StreamReaderImpl;

public class JsFunctions {
  public static final String[] JS_METHODS = new String[] {
    "ns", "xml", "parseXml"
  };
  private static final ReaderConfig readerCfg = new ReaderConfig();
  static { readerCfg.configureForSpeed(); }

  public static Namespace ns(String prefix, String uri) { return getNamespace(prefix, uri); }
  public static JdomBuilder xml(String root, Object ns) {
    return new JdomBuilder(root, cast(ns, Namespace.class));
  }
  public static Document parseXml(String in) {
    try {
      return new StAXStreamBuilder().build(StreamReaderImpl.construct(
          CharSourceBootstrapper.construct(readerCfg, new StringReader(in))));
    } catch (JDOMException | XMLStreamException e) { return sneakyThrow(e); }
  }

  private static <T> T cast(Object o, Class<T> c) {
    return o == Undefined.instance? null
           : o instanceof NativeJavaObject? (T)((NativeJavaObject)o).unwrap()
           : (T)o;
  }
}
