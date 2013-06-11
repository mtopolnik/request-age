function conf(b) {
}

function init() {
   nsdecl("a", "http://www.w3.org/2005/Atom");
   req.acceptableStatus("ok");
   req.responseDefault("xml");
   test(); 
}

function test() {
   req("get").get("http://localhost:8080/g").go(function(r) {
//      log.debug("get response {}", r.getResponseBody());
      req("post").post(r.getResponseBody()).body(
            xml("root", ns("a")).el("child").textel("txt", "g")
      ).go(function(r) {
    	  req("get2").get("http://localhost:8080/"
    			  + xpath("/a:root/a:child/a:txt/text()").evaluate(r.body())[0])
    	  .go(null);
      })
   });
}
