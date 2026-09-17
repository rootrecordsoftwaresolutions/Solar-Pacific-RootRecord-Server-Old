(function () {
  var WINDOWS = ["1h", "8h", "12h", "24h", "48h", "7d", "1m", "year"];

  function q(sel) {
    return document.querySelector(sel);
  }

  function param(name) {
    var m = location.search.match(new RegExp("[?&]" + name + "=([^&]+)"));
    return m ? decodeURIComponent(m[1]) : "";
  }

  function tokenSuffix() {
    var t = param("token");
    return t ? "?token=" + encodeURIComponent(t) : "";
  }

  function fmt(n, unit) {
    if (n == null || !Number.isFinite(Number(n))) return "—";
    var v = Number(n);
    var s =
      Math.abs(v) >= 1000
        ? v.toLocaleString(undefined, { maximumFractionDigits: 2 })
        : v.toLocaleString(undefined, { maximumFractionDigits: 3 });
    return unit ? s + " " + unit : s;
  }

  function fmtPct(n) {
    if (n == null || !Number.isFinite(Number(n))) return "—";
    var v = Number(n);
    var sign = v > 0 ? "+" : "";
    return sign + v.toFixed(2) + "%";
  }

  function pctClass(n) {
    if (n == null || !Number.isFinite(Number(n))) return "";
    if (Number(n) > 0) return "pct-up";
    if (Number(n) < 0) return "pct-down";
    return "";
  }

  function windowTable(summary, unit) {
    var cur = summary.pct_change || {};
    var avg = summary.pct_change_average || summary.pct_change || {};
    var tot = summary.pct_change_total || summary.pct_change || {};
    var head =
      "<tr><th>Window</th><th>Current %Δ</th><th>Average %Δ</th><th>Total %Δ</th></tr>";
    var body = WINDOWS.map(function (w) {
      return (
        "<tr><td>" +
        w +
        '</td><td class="' +
        pctClass(cur[w]) +
        '">' +
        fmtPct(cur[w]) +
        '</td><td class="' +
        pctClass(avg[w]) +
        '">' +
        fmtPct(avg[w]) +
        '</td><td class="' +
        pctClass(tot[w]) +
        '">' +
        fmtPct(tot[w]) +
        "</td></tr>"
      );
    }).join("");
    return (
      '<div class="webstat-summary-nums">' +
      '<div class="webstat-stat"><span>Current</span><strong>' +
      fmt(summary.current, unit) +
      "</strong></div>" +
      '<div class="webstat-stat"><span>Average</span><strong>' +
      fmt(summary.average, unit) +
      "</strong></div>" +
      '<div class="webstat-stat"><span>Total</span><strong>' +
      fmt(summary.total, unit) +
      "</strong></div>" +
      '<div class="webstat-stat"><span>Count</span><strong>' +
      (summary.count != null ? String(summary.count) : "—") +
      "</strong></div></div>" +
      '<table class="webstat-table webstat-windows"><thead>' +
      head +
      "</thead><tbody>" +
      body +
      "</tbody></table>"
    );
  }

  function rowsTable(data) {
    var rows = data.rows || [];
    if (!rows.length) return '<p class="webstat-meta">No rows.</p>';
    if (rows[0].key != null) {
      return (
        '<table class="webstat-table"><thead><tr><th>Key</th><th>Value</th></tr></thead><tbody>' +
        rows
          .map(function (r) {
            return "<tr><td>" + (r.key || "") + "</td><td>" + (r.value != null ? r.value : "—") + "</td></tr>";
          })
          .join("") +
        "</tbody></table>"
      );
    }
    var labelKey = rows[0].player != null ? "player" : rows[0].listing != null ? "listing" : "player";
    var valueKey =
      rows[0].balance != null
        ? "balance"
        : rows[0].seconds != null
          ? "seconds"
          : rows[0].amount != null
            ? "amount"
            : rows[0].price != null
              ? "price"
              : rows[0].online != null
                ? "online"
                : "value";
    return (
      '<table class="webstat-table"><thead><tr><th>' +
      labelKey +
      "</th><th>" +
      valueKey +
      "</th></tr></thead><tbody>" +
      rows
        .map(function (r) {
          var label = r[labelKey] != null ? r[labelKey] : r.label || "";
          var val = r[valueKey];
          return (
            "<tr><td>" +
            label +
            "</td><td>" +
            fmt(val, data.unit || "") +
            "</td></tr>"
          );
        })
        .join("") +
      "</tbody></table>"
    );
  }

  function render(data) {
    var set = data.id || param("set") || "dataset";
    q("#title").textContent = data.title || set;
    document.title = (data.title || set) + " · Webstat · RootMC";
    q("#meta").textContent =
      (data.server_name || "") +
      " · " +
      (data.server_id || "") +
      " · " +
      (data.computed_at || "—") +
      (data.error ? " · error: " + data.error : "");
    q("#api-link").href = "/api/data/" + encodeURIComponent(set) + ".json" + tokenSuffix();
    q("#api-link").textContent = "/api/data/" + set + ".json";
    var summary = data.summary || {};
    q("#summary").innerHTML = "<h2>Summary</h2>" + windowTable(summary, data.unit || "");
    q("#rows").innerHTML = rowsTable(data);
  }

  var set = param("set") || "balances";
  fetch("/api/data/" + encodeURIComponent(set) + ".json" + tokenSuffix(), { cache: "no-store" })
    .then(function (r) {
      if (!r.ok) throw new Error("HTTP " + r.status);
      return r.json();
    })
    .then(render)
    .catch(function (e) {
      q("#meta").textContent = "Failed to load dataset: " + e.message;
    });
})();
