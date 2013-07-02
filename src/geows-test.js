function logResult(r) {
   var json = r.stringBody();
   log.debug("Response: {}",json);
}

function conf(b) {
   format = java.lang.String.format;
   base = "http://localhost:4180/geows";
}

function init() {
   req.acceptableStatus("ok");
   test(); 
}

function test() {
   req("suggest-street").get(spy("suggest URL", url(base).s("suggest","street") 
         .q("zip","10000", "place","zagreb", "street","Heinzelo")))
         .go(logResult);
   req("validation-success").get(spy("validation URL", url(base).s("validation")
         .q("zip","10000", "place","zagreb", "street","Heinzelova", "number","47", "subn","b")))
         .go(logResult);
   req("validation-street").get(spy("validation URL", url(base).s("validation")
         .q("zip","10000", "place","zagreb", "street","Heinzelova", "number","11", "subn","b")))
         .go(logResult);
   req("validation-place").get(spy("validation URL", url(base).s("validation")
         .q("zip","10000", "place","zagreb", "street","Hein", "number","47", "subn","b")))
         .go(logResult);
   req("validation-invalid").get(spy("validation URL", url(base).s("validation")
         .q("zip","10000", "place","zagb", "street","Hein", "number","47", "subn","b")))
         .go(logResult);
}
