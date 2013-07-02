function conf(b) {
}

function init() {
   base = "http://localhost:8080";
   var url1 = url(base).s("g");
   req("get").get(url1).go(function(r) {
      req("post").post(r.stringBody()).body([{a:1},{b:2}]).go(function(r) {
         log.debug("Stringify jsonBody {}", JSON.stringify(r.jsonBody()));
      })
   });
}
