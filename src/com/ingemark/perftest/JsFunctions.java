package com.ingemark.perftest;

import static com.ingemark.perftest.Util.sneakyThrow;
import static org.jdom2.Namespace.getNamespace;

import java.io.StringReader;

import javax.xml.stream.XMLStreamException;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.StAXStreamBuilder;

import com.fasterxml.aalto.in.CharSourceBootstrapper;
import com.fasterxml.aalto.in.ReaderConfig;
import com.fasterxml.aalto.stax.StreamReaderImpl;

public class JsFunctions {
  static final String[] JS_METHODS = new String[] {
    "ns", "nxml", "xml", "parseXml"
  };
  public static Namespace ns(String prefix, String uri) { return getNamespace(prefix, uri); }
  public static JdomBuilder xml(String root) { return new JdomBuilder(root, null); }
  public static JdomBuilder nxml(String root, String prefix, String uri) {
    return new JdomBuilder(root, ns(prefix, uri));
  }
  public static Document parseXml(String in) {
    try {
      final ReaderConfig cfg = new ReaderConfig();
      cfg.configureForSpeed();
      return new StAXStreamBuilder().build(StreamReaderImpl.construct(
          CharSourceBootstrapper.construct(cfg, new StringReader(in))));
    } catch (JDOMException | XMLStreamException e) { return sneakyThrow(e); }
  }
}
