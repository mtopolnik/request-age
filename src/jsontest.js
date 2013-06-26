function conf(b) {
   format = java.lang.String.format;
   urlBase = "http://localhost:8080";
}

function init() {
   req.acceptableStatus("ok");
   test(); 
}

function test() {
   req("get").get(format("%s/g",urlBase)).go(function(r) {
      req("post").post(r.stringBody()).body({root:{child:{txt:"g"}}})
      .go(function(r) {
    	  req("get22").get(format("%s/%s", urlBase, r.jsonBody().root.child.txt))
    	  .go(null);
      })
   });
}
