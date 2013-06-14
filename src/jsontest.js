function conf(b) {
}

function init() {
   req.acceptableStatus("ok");
   test(); 
}

function test() {
   req("get").get("http://localhost:8080/g").go(function(r) {
      req("post").post(r.stringBody()).body({root:{child:{txt:"g"}}})
      .go(function(r) {
    	  req("get22").get("http://localhost:8080/" + r.jsonBody().root.child.txt)
    	  .go(null);
      })
   });
}
