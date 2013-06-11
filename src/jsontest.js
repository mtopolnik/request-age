function conf(b) {
}

function init() {
   req.acceptableStatus("ok");
   req.responseDefault("json");
   test(); 
}

function test() {
   req("get").get("http://localhost:8080/g").go(function(r) {
//      log.debug("get response {}", r.getResponseBody());
      req("post").post(r.getResponseBody()).body({root:{child:{txt:"g"}}})
      .go(function(r) {
    	  req("get2").get("http://localhost:8080/" + r.body().root.child.txt)
    	  .go(null);
      })
   });
}
