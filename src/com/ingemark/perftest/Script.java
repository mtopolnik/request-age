package com.ingemark.perftest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

public class Script
{
  public final List<RequestProvider> initReqs, testReqs;
  final Map<String, String> initEnv;

  public Script(List<RequestProvider> initReqs, List<RequestProvider> testReqs,
      Map<String, String> initEnv) {
    this.initReqs = initReqs;
    this.testReqs = testReqs;
    this.initEnv = initEnv;
  }

  public Instance newInstance(AsyncHttpClient client) { return new Instance(client); }

  public class Instance {
    final Iterator<RequestProvider> reqIterator = testReqs.iterator();
    final Map<String, Object> env = new HashMap<String, Object>(initEnv);
    final AsyncHttpClient client;

    Instance(AsyncHttpClient client) { this.client = client; }

    public RequestProvider nextRequestProvider() {
      return reqIterator.hasNext()? reqIterator.next() : null;
    }

    public void result(Response resp, boolean isSuccess) {

    }
  }
}
