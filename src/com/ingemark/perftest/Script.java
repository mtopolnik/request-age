package com.ingemark.perftest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.ning.http.client.Response;

public class Script
{
  public final List<RequestProvider> initReqs, testReqs;
  final Map<String, String> initEnv;

  public Script(List<RequestProvider> initReqs, List<RequestProvider> testReqs,
      Map<String, String> initEnv)
  {
    this.initReqs = initReqs;
    this.testReqs = testReqs;
    this.initEnv = initEnv;
  }

  public Instance warmupInstance() { return new Instance(true); }

  public Instance testInstance() { return new Instance(false); }

  public class Instance {
    final Iterator<RequestProvider> reqIterator;
    final Map<String, Object> env = new HashMap<String, Object>(initEnv);

    Instance(boolean warmup) {
      reqIterator = warmup?
          concat(initReqs.iterator(), testReqs.iterator()) : testReqs.iterator();
    }

    public RequestProvider nextRequestProvider() {
      return reqIterator.hasNext()? reqIterator.next() : null;
    }

    public boolean result(Response resp) { return isSuccessResponse(resp); }
  }

  public ArrayList<Integer> getInit() {
    final ArrayList<Integer> ret = new ArrayList<>();
    for (RequestProvider rp : testReqs) ret.add(rp.liveStats.index);
    return ret;
  }

  static boolean isSuccessResponse(Response r) {
    return r != null && r.getStatusCode() >= 200 && r.getStatusCode() < 400;
  }

  static <T> Iterator<T> concat(final Iterator<T> it1, final Iterator<T> it2) {
    return new Iterator<T>() {
      Iterator<T> it = it1;
      @Override public boolean hasNext() {
        if (it.hasNext()) return true;
        it = it2;
        return it.hasNext();
      }
      @Override public T next() {
        if (hasNext()) return it.next();
        throw new NoSuchElementException();
      }
      @Override public void remove() { throw new UnsupportedOperationException(); }
    };
  }
}
