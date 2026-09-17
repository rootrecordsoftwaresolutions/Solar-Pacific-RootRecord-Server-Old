(function () {
  function q(sel) {
    return document.querySelector(sel);
  }

  function tokenSuffix() {
    var m = location.search.match(/[?&]token=([^&]+)/);
    return m ? "?token=" + encodeURIComponent(decodeURIComponent(m[1])) : "";
  }

  function withToken(url) {
    var t = tokenSuffix();
    if (!t) return url;
    return url + (url.indexOf("?") >= 0 ? "&" : "?") + t.slice(1);
  }

  var timer = null;

  function render(data) {
    q("#server-name").textContent = (data.server || "Server") + " · logs";
    document.title = (data.server || "Server") + " · Logs · Webstat · RootMC";
    q("#meta").textContent =
      (data.path || "latest.log") +
      " · " +
      (data.line_count || 0) +
      " lines" +
      (data.truncated ? " (truncated)" : "") +
      " · " +
      (data.size_bytes != null ? data.size_bytes + " B" : "");
    var lines = data.lines || [];
    q("#log").textContent = lines.length ? lines.join("\n") : "(empty)";
    var pre = q("#log");
    pre.scrollTop = pre.scrollHeight;
  }

  function load() {
    fetch(withToken("/api/logs/tail.json?lines=200"), { cache: "no-store" })
      .then(function (r) {
        if (!r.ok) throw new Error("HTTP " + r.status);
        return r.json();
      })
      .then(render)
      .catch(function (e) {
        q("#meta").textContent = "Failed to load log: " + e.message;
      });
  }

  function schedule() {
    if (timer) clearInterval(timer);
    timer = null;
    if (q("#auto-refresh").checked) {
      timer = setInterval(load, 10000);
    }
  }

  q("#back-home").href = "/" + tokenSuffix().replace("?", "?");
  if (!tokenSuffix()) q("#back-home").href = "/";
  else q("#back-home").href = "/" + tokenSuffix();

  q("#btn-download").href = withToken("/api/logs/latest.log");
  q("#link-tail").href = withToken("/api/logs/tail.json?lines=200");
  q("#btn-refresh").addEventListener("click", load);
  q("#auto-refresh").addEventListener("change", schedule);

  load();
  schedule();
})();
