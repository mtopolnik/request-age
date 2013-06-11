function conf(b) {
}

function init() {
   req.accept("ok");
   test(); 
}

function test() {
   req("get").get("http://localhost:8080/g").go(function(r) {
//      log.debug("get response {}", r.getResponseBody());
      req("post").post(r.getResponseBody()).body({root:{child:{txt:"g"}}})
      .go(function(r) {
    	  req("get2").get("http://localhost:8080/"
    			  + JSON.parse(r.getResponseBody()).root.child.txt)
    	  .go(null);
      })
   });
}
