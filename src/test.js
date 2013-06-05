jst.http("GET", "http://www.google.hr", function(resp) {
java.lang.System.out.println("hr " + resp.getStatusText())
jst.http("GET", "http://www.google.com", function(resp2) {
java.lang.System.out.println("com " + resp2.getStatusText())
})})
