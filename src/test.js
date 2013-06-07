function configure(b) {
	out = java.lang.System.out;
}

function init() {
	$("").get("http://www.siemens.com").go(function(resp) {
		out.println("Response received!!!!!!!")
	});
	$.post("http://www.siemens.de").go(function(resp) {
	   out.println("Response received! AGAIN!!!!!!")
	});
}
