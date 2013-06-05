function configure(b) {
   println = java.lang.System.out.println;
}

function init() {
   $.nHttp("GET", "http://www.google.hr", function(resp) {
      println("hr " + resp.getStatusText());
      stat1 = resp.getStatusText();
   });
   $.nHttp("GET", "http://www.google.com", function(resp) {
      println("com " + resp.getStatusText());
      stat2 = resp.getStatusText();
   });
   test();
}

function test() {
   $.http("google.de", "GET", "http://www.google.de", function(resp) {
      println("de " + resp.getStatusText());
      $.http("siemens.de", "GET", "http://www.siemens.de", function(resp) {
         println("siemens.de " + resp.getStatusText());
      });
   });
}