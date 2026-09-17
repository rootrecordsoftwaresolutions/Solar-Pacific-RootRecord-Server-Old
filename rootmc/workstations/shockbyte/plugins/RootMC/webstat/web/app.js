(function () {
  function q(sel) {
    return document.querySelector(sel);
  }

  function tokenSuffix() {
    var m = location.search.match(/[?&]token=([^&]+)/);
    return m ? "?token=" + encodeURIComponent(decodeURIComponent(m[1])) : "";
  }

  function withToken(path) {
    var t = tokenSuffix();
    if (!t) return path;
    return path + (path.indexOf("?") >= 0 ? "&" : "?") + t.slice(1);
  }

  function render(data) {
    q("#server-name").textContent = data.server_name || "Server";
    document.title = (data.server_name || "Server") + " · Webstat · RootMC";
    q("#meta").textContent =
      "server " +
      (data.server_id || "?") +
      " · computed " +
      (data.computed_at || "—") +
      " · windows " +
      ((data.windows || []).join(" · ") || "—");
    var list = data.datasets || [];
    q("#root").innerHTML = list.length
      ? list
          .map(function (d) {
            var href = d.href || "/data.html?set=" + encodeURIComponent(d.id);
            href = withToken(href);
            return (
              '<a class="webstat-catalog-item" href="' +
              href +
              '"><strong>' +
              (d.title || d.id) +
              "</strong><span>" +
              (d.description || "") +
              "</span><code>" +
              (d.api || "") +
              "</code></a>"
            );
          })
          .join("")
      : '<p class="webstat-meta">No datasets yet — wait for the next recompute.</p>';
  }

  var linkLogs = q("#link-logs");
  if (linkLogs) linkLogs.href = withToken("/logs/");
  var tail = document.querySelector('a[href^="/api/logs/tail"]');
  if (tail) tail.href = withToken("/api/logs/tail.json?lines=200");

  fetch("/api/health" + tokenSuffix(), { cache: "no-store" })
    .then(function (r) {
      return r.ok ? r.json() : null;
    })
    .then(function (h) {
      var nav = q("#local-nav");
      if (!nav) return;
      if (h && h.features && h.features.logs_page === false) {
        nav.hidden = true;
      }
    })
    .catch(function () {});

  fetch("/api/data/index.json" + tokenSuffix(), { cache: "no-store" })
    .then(function (r) {
      if (!r.ok) throw new Error("HTTP " + r.status);
      return r.json();
    })
    .then(render)
    .catch(function (e) {
      q("#meta").textContent = "Failed to load catalog: " + e.message;
    });
})();
