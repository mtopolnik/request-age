function configure(b) {
   out = java.lang.System.out;
}

function init() {
   $.nHttp("GET", "http://www.google.hr", function(resp) {
      out.println("hr " + resp.getStatusText());
      stat1 = resp.getStatusText();
   });
   $.nHttp("GET", "http://www.google.com", function(resp) {
      out.println("com " + resp.getStatusText());
      stat2 = resp.getStatusText();
   });
   test();
}

function test() {
   $.http("google.de", "GET", "http://www.google.de", function(resp) {
      $.http("siemens.de", "GET", "http://www.siemens.de", function(resp) {
         $.http("siemens.hr", "GET", "http://www.siemens.hr", function(resp) {
         });
      });
   });
}