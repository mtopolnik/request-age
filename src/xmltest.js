function conf(b) {
}

function init() {
   nsdecl("a", "http://www.w3.org/2005/Atom");
   req.acceptableStatus("ok");
   test(); 
}

function test() {
	return req("get").get("http://localhost:8080/g").go(function(r) {
	   return req("post").post(r.stringBody()).body(
	         xml("root", ns("a")).el("child").textel("txt", "g")
      ).go(function(r) {
    	  return req("get2").get("http://localhost:8080/" + 
    			  xpath("/a:root/a:child/a:txt/text()").evaluate(r.xmlBody())[0])
    	  .go(function(r) { throw FAILED_RESPONSE; });
      })
   });
}
