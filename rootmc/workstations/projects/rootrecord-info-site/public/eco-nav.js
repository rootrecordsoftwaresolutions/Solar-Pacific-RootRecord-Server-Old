/** Shared RootRecord · RootMC · Ava switcher for static Root Record pages. */
(function () {
  if (document.querySelector(".eco-bar")) return;
  var host = (location.hostname || "").toLowerCase();
  var path = location.pathname || "/";
  var active = "rootrecord";
  if (host.indexOf("rootmc") >= 0) active = "rootmc";
  if (path.indexOf("/ava") === 0 || host.indexOf("ava.") === 0) active = "ava";
  var items = [
    { href: "https://rootrecord.info/", key: "rootrecord", label: "RootRecord" },
    { href: "https://rootmc.net/", key: "rootmc", label: "RootMC" },
    { href: "https://avaivy.cloud/wiki/", key: "ava", label: "Ava" },
  ];
  var bar = document.createElement("nav");
  bar.className = "eco-bar";
  bar.setAttribute("aria-label", "Ecosystem");
  bar.innerHTML = items
    .map(function (it) {
      var on = it.key === active ? ' aria-current="page"' : "";
      return '<a href="' + it.href + '"' + on + ">" + it.label + "</a>";
    })
    .join("");
  function mount() {
    if (document.querySelector(".eco-bar")) return;
    if (document.body) document.body.insertBefore(bar, document.body.firstChild);
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount);
  else mount();
})();
