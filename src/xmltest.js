function conf(b) {
}

function init() {
   base = "http://localhost:8080";
   test(); 
}

function test() {
   url1 = url(base).s("g");
   req("get").get(url1).go(function(r) {
      req("post").post(r.stringBody()).body(
            spy("Posting xml", xml("root").el("child").textel("txt", "g"))
      ).go(function(r) {
         req("get2").get(
               url(base).s(xpath("/root/child/txt/text()").evaluate(r.xmlBody())[0])
         ).go();
      })
   });
}
