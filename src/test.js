function configure(b) {
	out = java.lang.System.out;
}

function init() {
	$.get("http://www.siemens.com").go(function(resp) {
		out.println("Response received!!!!!!!")
	});
	$.post("http://www.siemens.de").go(function(resp) {
	   out.println("Response received! AGAIN!!!!!!")
	});
	test();
}

function test() {
   $("siemens.com").get("http://www.siemens.com").go(function(resp) {
      $("siemens.de").post("http://www.siemens.de").go(function(resp) {
         $("siemens.hr").post("http://www.siemens.hr").go(function(resp) {
            $("siemens.at").post("http://www.siemens.at").go(function(resp) {
            });
         });
      });
   });
}