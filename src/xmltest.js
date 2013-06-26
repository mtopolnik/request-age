function conf(b) {
}

function init() {
   nsdecl("a", "http://www.w3.org/2005/Atom");
   urlBase = "http://localhost:8080";
   req.acceptableStatus("ok");
   i = java.util.concurrent.atomic.AtomicInteger();
   return test(); 
}

function test() {
   req("get").get(urlBase+"/g").go(function(r) {
      req("post").post(r.stringBody()).body(
            spy("Posting xml", xml("root", ns("a")).el("child").textel("txt", "g"))
      ).go(function(r) {
         var xml = r.xmlBody();
         log.debug("get2 result: {}", prettify(xml)); 
         req("get2").get(urlBase + "/" + 
               xpath("/a:root/a:child/a:txt/text()").evaluate(xml)[0])
               .go();
      })
   });
}
