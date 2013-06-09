function conf(b) {
	out = java.lang.System.out;
	nsdecl("a", "http://www.w3.org/2005/Atom");
}

function init() { test(); }

function test() {
	req("feeds").get("http://stackoverflow.com/feeds").go(function(resp) {
	   var body = parseXml(resp);
	   var id = xpath("/a:feed/a:entry[1]/a:id/text()").evaluateFirst(body);
//	   out.println("id " + id);
	   req("first entry").get(id).go(function(resp2) {
//		   out.println(resp2.getResponseBody().substring(0, 80))
	   })
	});
}
