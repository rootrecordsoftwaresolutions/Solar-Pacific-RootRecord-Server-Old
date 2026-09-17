/**
 * Account dashboard panels on /account.
 *
 * account.js owns auth, the sensitive-change gate, and the forms; it broadcasts the `/v1/me`
 * payload as `rootrecord-account-me` once loaded. This file renders everything built on top of
 * that: the identity hero, membership, connected apps, active sessions, and notification
 * preferences. Every panel loads independently and degrades to an inline retry so one failing
 * endpoint never blanks the page.
 */
(function () {
  const TOKEN_KEY = "rootrecord_portal_token";
  const ME_EVENT = "rootrecord-account-me";
  /** How long to wait for account.js before fetching `/v1/me` ourselves. */
  const ME_EVENT_GRACE_MS = 4000;
  const REQUEST_TIMEOUT_MS = 15000;

  /** Roots/RRTT surfaces stay hidden while the promo phase is running. */
  const HIDDEN_APP_IDS = new Set(["rootrecord_token_manager_android"]);
  const HIDDEN_APP_PATTERN = /root[\s_-]*units?|\broots\b|rrtt/i;

  let apiBase = "";
  let me = null;
  let panelsStarted = false;

  function el(id) {
    return document.getElementById(id);
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function isTruthy(value) {
    const v = String(value == null ? "" : value).trim().toLowerCase();
    return v === "1" || v === "true" || v === "yes";
  }

  function parseDate(raw) {
    const s = String(raw == null ? "" : raw).trim();
    if (!s) return null;
    const t = Date.parse(s);
    return Number.isNaN(t) ? null : new Date(t);
  }

  function fmtDate(raw) {
    const d = parseDate(raw);
    if (!d) return "—";
    try {
      return d.toLocaleDateString(undefined, { dateStyle: "medium" });
    } catch {
      return d.toISOString().slice(0, 10);
    }
  }

  function fmtDateTime(raw) {
    const d = parseDate(raw);
    if (!d) return "—";
    try {
      return d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
    } catch {
      return d.toISOString();
    }
  }

  function fmtRelative(raw) {
    const d = parseDate(raw);
    if (!d) return "";
    const secs = Math.round((Date.now() - d.getTime()) / 1000);
    if (secs < 90) return "just now";
    const mins = Math.round(secs / 60);
    if (mins < 60) return mins + (mins === 1 ? " minute ago" : " minutes ago");
    const hours = Math.round(mins / 60);
    if (hours < 24) return hours + (hours === 1 ? " hour ago" : " hours ago");
    const days = Math.round(hours / 24);
    if (days < 30) return days + (days === 1 ? " day ago" : " days ago");
    return fmtDate(raw);
  }

  async function loadApiBase() {
    try {
      const res = await fetch("/api/site-config", { cache: "no-store" });
      if (!res.ok) return;
      const j = await res.json();
      if (j && typeof j.apiBase === "string") apiBase = j.apiBase.replace(/\/+$/, "");
    } catch {
      // Same-origin Pages proxy handles /v1 and /api, so an empty base is a valid fallback.
    }
  }

  async function api(path, opts) {
    const headers = new Headers(opts && opts.headers);
    const token = localStorage.getItem(TOKEN_KEY);
    if (token && !headers.has("Authorization")) headers.set("Authorization", "Bearer " + token);
    if (opts && opts.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      return await fetch(apiBase + path, {
        ...(opts || {}),
        headers,
        credentials: "include",
        signal: controller.signal,
      });
    } finally {
      clearTimeout(timer);
    }
  }

  async function getJson(path) {
    try {
      const res = await api(path, { cache: "no-store" });
      const text = await res.text();
      let data = null;
      try {
        data = text ? JSON.parse(text) : null;
      } catch {
        data = null;
      }
      return { ok: res.ok, status: res.status, data };
    } catch {
      return { ok: false, status: 0, data: null };
    }
  }

  function rowsHtml(rows) {
    return rows
      .filter((r) => r && r[1] != null && r[1] !== "")
      .map(
        ([k, v]) =>
          '<div class="account-row"><span class="account-k">' +
          escapeHtml(k) +
          '</span><span class="account-v">' +
          v +
          "</span></div>",
      )
      .join("");
  }

  function badge(text, kind) {
    return '<span class="acct-badge acct-badge-' + kind + '">' + escapeHtml(text) + "</span>";
  }

  function showRetry(container, message, retry) {
    if (!container) return;
    container.innerHTML =
      '<p class="acct-empty">' +
      escapeHtml(message) +
      ' <button type="button" class="acct-retry">Retry</button></p>';
    const btn = container.querySelector(".acct-retry");
    if (btn) btn.addEventListener("click", retry);
  }

  function membershipLabel(data) {
    const access = data && typeof data.access === "object" ? data.access : {};
    const tier = String(data.tier || access.tier || "").trim().toLowerCase();
    const status = String(data.subscription_status || "").trim().toLowerCase();
    if (isTruthy(data.life_member) || isTruthy(data.lifeMember) || tier === "life" || tier === "lifetime") {
      return { label: "Lifetime member", kind: "ok" };
    }
    if (status === "active") return { label: "Member", kind: "ok" };
    if (status === "trialing" || status === "trial") return { label: "Trial", kind: "ok" };
    if (status === "past_due") return { label: "Past due", kind: "warn" };
    if (status === "canceled") return { label: "Canceled", kind: "warn" };
    if (isTruthy(data.pro_unlocked) || isTruthy(data.proUnlocked)) return { label: "Member", kind: "ok" };
    return { label: "Free", kind: "muted" };
  }

  function renderHero(data) {
    const email = String(data.email || "").trim();
    const displayName = String(data.public_display_name || "").trim();
    const heroName = el("acct-hero-name");
    const heroEmail = el("acct-hero-email");
    const avatar = el("acct-avatar");
    const since = el("acct-hero-since");
    const idText = el("acct-hero-account-id");
    const copyBtn = el("acct-copy-id");
    const badges = el("acct-badges");

    const primary = displayName || email.split("@")[0] || "Your account";
    if (heroName) heroName.textContent = primary;
    if (heroEmail) heroEmail.textContent = email || "—";
    if (avatar) avatar.textContent = (primary.trim()[0] || "R").toUpperCase();
    if (since) since.textContent = fmtDate(data.account_created_at);

    const accountId = String(data.account_id || "").trim();
    if (idText) idText.textContent = accountId || "—";
    if (copyBtn) {
      copyBtn.hidden = !accountId;
      if (accountId && !copyBtn.dataset.bound) {
        copyBtn.dataset.bound = "1";
        copyBtn.addEventListener("click", async function () {
          try {
            await navigator.clipboard.writeText(accountId);
            copyBtn.textContent = "Copied";
          } catch {
            copyBtn.textContent = "Press Ctrl+C";
          }
          setTimeout(() => {
            copyBtn.textContent = "Copy";
          }, 1800);
        });
      }
    }

    if (badges) {
      const plan = membershipLabel(data);
      const parts = [badge(plan.label, plan.kind)];
      parts.push(
        data.account_verified
          ? badge("Verified", "ok")
          : badge("Unverified", "warn"),
      );
      if (data.discord_linked) {
        const handle = String(data.discord_username || data.discord_global_name || "").trim();
        parts.push(badge(handle ? "Discord: " + handle : "Discord linked", "info"));
      }
      if (isTruthy(data.developer_unlimited)) parts.push(badge("Developer", "info"));
      badges.innerHTML = parts.join("");
    }
  }

  function renderMembership(data) {
    const body = el("acct-membership-body");
    const tag = el("acct-membership-tag");
    const foot = el("acct-membership-foot");
    if (!body) return;

    const plan = membershipLabel(data);
    if (tag) {
      tag.hidden = false;
      tag.textContent = plan.label;
      tag.className = "acct-card-tag acct-tag-" + plan.kind;
    }

    const access = data && typeof data.access === "object" ? data.access : {};
    const status = String(data.subscription_status || "").trim();
    const reason = String(access.reason || "").trim();
    const rows = [
      ["Plan", escapeHtml(plan.label)],
      ["Subscription", escapeHtml(status && status !== "none" ? status : "No paid subscription")],
      ["Access tier", escapeHtml(String(access.tier || "—"))],
    ];
    if (reason && reason !== "none") rows.push(["Access reason", escapeHtml(reason)]);
    if (data.pro_redeemed_until) rows.push(["Member access until", escapeHtml(fmtDateTime(data.pro_redeemed_until))]);
    if (isTruthy(data.developer_unlimited)) rows.push(["Developer access", "Unlimited"]);
    body.innerHTML = rowsHtml(rows);

    if (foot) foot.hidden = false;
  }

  function appIsHidden(id, name) {
    if (HIDDEN_APP_IDS.has(String(id || ""))) return true;
    return HIDDEN_APP_PATTERN.test(String(id || "") + " " + String(name || ""));
  }

  const APP_NAMES = {
    rootrecord_business_manager_android: "Business Manager (Android)",
    rootrecord_business_manager_windows: "Business Manager (Windows)",
    rootrecord_weather_manager_android: "Weather Manager (Android)",
    rootrecord_weather_manager_windows: "Weather Manager (Windows)",
    rootrecord_kilauea_alerts_android: "Kīlauea Alerts",
    rootrecord_account_hub_android: "Account Hub",
  };

  function appsFromMePayload(data) {
    const apps = data && typeof data.apps === "object" ? data.apps : {};
    const out = [];
    Object.keys(apps).forEach(function (key) {
      if (key === "usage" || key === "signals") return;
      const entry = apps[key] && typeof apps[key] === "object" ? apps[key] : {};
      // Legacy slots report `associated: null`, meaning "never tracked" rather than "not linked".
      if (entry.associated == null && !entry.last_connected_at) return;
      const fallbackName = key
        .replace(/^rootrecord_/, "")
        .replace(/_/g, " ")
        .replace(/\b\w/g, (c) => c.toUpperCase());
      out.push({
        id: key,
        name: APP_NAMES[key] || fallbackName,
        entitlement: "",
        last_seen_at: entry.last_connected_at || null,
        associated: entry.associated,
      });
    });
    return out;
  }

  function renderApps(list, note) {
    const body = el("acct-apps-body");
    const tag = el("acct-apps-tag");
    if (!body) return;

    const visible = list.filter((a) => !appIsHidden(a.id, a.name));
    if (tag) {
      tag.hidden = false;
      tag.textContent = String(visible.length);
      tag.className = "acct-card-tag acct-tag-muted";
    }
    if (!visible.length) {
      body.innerHTML = '<p class="acct-empty">No apps linked to this account yet.</p>';
      return;
    }

    body.innerHTML =
      '<ul class="acct-list">' +
      visible
        .map(function (app) {
          const name = String(app.name || app.id || "App").trim();
          const ent = String(app.entitlement || "").trim().toLowerCase();
          const entBadge = ent
            ? badge(ent === "free" ? "Free" : ent.charAt(0).toUpperCase() + ent.slice(1), ent === "free" ? "muted" : "ok")
            : "";
          const seen = app.last_seen_at
            ? "Last used " + escapeHtml(fmtRelative(app.last_seen_at))
            : app.associated === false
              ? "Not signed in yet"
              : "No recent activity";
          const store = app.play_store_url
            ? ' &middot; <a href="' + escapeHtml(app.play_store_url) + '" target="_blank" rel="noopener">Play Store</a>'
            : "";
          return (
            '<li class="acct-list-item"><div class="acct-list-main"><span class="acct-list-name">' +
            escapeHtml(name) +
            "</span>" +
            entBadge +
            '</div><p class="acct-list-meta">' +
            seen +
            store +
            "</p></li>"
          );
        })
        .join("") +
      "</ul>" +
      (note ? '<p class="acct-empty">' + escapeHtml(note) + "</p>" : "");
  }

  async function loadApps() {
    const body = el("acct-apps-body");
    if (!body) return;
    const res = await getJson("/api/me/apps");
    if (res.ok && Array.isArray(res.data) && res.data.length) {
      renderApps(res.data, "");
      return;
    }
    // Fallback: the `/v1/me` payload carries per-app association slots.
    if (me) {
      const fallback = appsFromMePayload(me);
      if (fallback.length) {
        renderApps(fallback, "Live app details are unavailable right now.");
        return;
      }
    }
    if (res.ok) {
      body.innerHTML = '<p class="acct-empty">No apps linked to this account yet.</p>';
      return;
    }
    showRetry(body, "Could not load your apps.", loadApps);
  }

  function sessionDeviceLabel(session) {
    const ua = String(session.user_agent || "");
    const device = String(session.device_id || "").trim();
    let platform = "";
    if (/android/i.test(ua)) platform = "Android";
    else if (/iphone|ipad|ios/i.test(ua)) platform = "iOS";
    else if (/windows/i.test(ua)) platform = "Windows";
    else if (/mac os|macintosh/i.test(ua)) platform = "macOS";
    else if (/linux/i.test(ua)) platform = "Linux";
    else if (/curl|python|okhttp/i.test(ua)) platform = "API client";
    if (/chrome/i.test(ua)) platform += platform ? " · Chrome" : "Chrome";
    else if (/firefox/i.test(ua)) platform += platform ? " · Firefox" : "Firefox";
    else if (/safari/i.test(ua) && !/chrome/i.test(ua)) platform += platform ? " · Safari" : "Safari";
    return platform || device || "Unknown device";
  }

  /** Generated device ids are opaque hashes; only surface ones a person would recognise. */
  function sessionDeviceHint(session) {
    const device = String(session.device_id || "").trim();
    if (!device || device.length > 24 || /^[0-9a-f]{16,}$/i.test(device)) return "";
    return device;
  }

  async function revokeSession(id, button) {
    if (!id) return;
    if (button) {
      button.disabled = true;
      button.textContent = "Signing out…";
    }
    const res = await api("/api/me/sessions/" + encodeURIComponent(id) + "/revoke", { method: "POST" });
    if (res && res.ok) {
      loadSessions();
      return;
    }
    if (button) {
      button.disabled = false;
      button.textContent = "Retry sign out";
    }
  }

  async function loadSessions() {
    const body = el("acct-sessions-body");
    const tag = el("acct-sessions-tag");
    if (!body) return;

    const res = await getJson("/api/me/sessions");
    if (!res.ok || !Array.isArray(res.data)) {
      if (tag) tag.hidden = true;
      showRetry(body, "Could not load your sessions.", loadSessions);
      return;
    }

    const sessions = res.data.filter((s) => s && !s.revoked);
    if (tag) {
      tag.hidden = false;
      tag.textContent = String(sessions.length);
      tag.className = "acct-card-tag acct-tag-muted";
    }
    if (!sessions.length) {
      body.innerHTML = '<p class="acct-empty">No active sessions.</p>';
      return;
    }

    body.innerHTML =
      '<ul class="acct-list">' +
      sessions
        .map(function (s) {
          const current = Boolean(s.current);
          const seen = s.last_seen_at ? "Active " + fmtRelative(s.last_seen_at) : "Created " + fmtRelative(s.created_at);
          const hint = sessionDeviceHint(s);
          return (
            '<li class="acct-list-item"><div class="acct-list-main"><span class="acct-list-name">' +
            escapeHtml(sessionDeviceLabel(s)) +
            "</span>" +
            (current ? badge("This device", "ok") : "") +
            '</div><p class="acct-list-meta">' +
            escapeHtml(seen) +
            (hint ? " &middot; " + escapeHtml(hint) : "") +
            "</p>" +
            (current
              ? ""
              : '<button type="button" class="acct-mini-btn" data-session-id="' +
                escapeHtml(s.id) +
                '">Sign out</button>') +
            "</li>"
          );
        })
        .join("") +
      "</ul>";

    body.querySelectorAll("[data-session-id]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        revokeSession(btn.getAttribute("data-session-id"), btn);
      });
    });
  }

  function toggleRowHtml(id, label, description, checked) {
    return (
      '<div class="acct-toggle-row"><label class="acct-toggle"><input type="checkbox" id="' +
      id +
      '"' +
      (checked ? " checked" : "") +
      '><span class="acct-toggle-track" aria-hidden="true"></span><span class="acct-toggle-label"><span class="acct-list-name">' +
      escapeHtml(label) +
      '</span><span class="acct-list-meta">' +
      escapeHtml(description) +
      "</span></span></label></div>"
    );
  }

  async function loadNotifications() {
    const body = el("acct-notifications-body");
    if (!body) return;

    const [prefs, locations, emails] = await Promise.all([
      getJson("/api/me/prefs"),
      getJson("/api/locations"),
      getJson("/v1/me/email-marketing-prefs"),
    ]);

    if (!prefs.ok && !locations.ok && !emails.ok) {
      showRetry(body, "Could not load your preferences.", loadNotifications);
      return;
    }

    let html = "";

    if (prefs.ok && prefs.data && typeof prefs.data === "object") {
      html += toggleRowHtml(
        "acct-pref-noaa",
        "Severe weather alerts",
        "NOAA watches, warnings, and advisories pushed to your devices.",
        Boolean(prefs.data.noaa_alerts_enabled),
      );
    }

    const rows = [];
    if (locations.ok && Array.isArray(locations.data)) {
      const n = locations.data.length;
      rows.push([
        "Saved locations",
        escapeHtml(n === 0 ? "None saved" : n === 1 ? "1 location" : n + " locations"),
      ]);
    }
    if (emails.ok && emails.data && typeof emails.data.preferences === "object") {
      const p = emails.data.preferences;
      const keys = ["general_newsletter", "kilauea_newsletter", "business_manager_newsletter", "simple_weather_newsletter"];
      const on = keys.filter((k) => Boolean(p[k])).length;
      rows.push(["Email newsletters", escapeHtml(on + " of " + keys.length + " enabled")]);
      if (p.updated_at) rows.push(["Preferences updated", escapeHtml(fmtRelative(p.updated_at))]);
    }
    if (rows.length) html += '<div class="acct-kv">' + rowsHtml(rows) + "</div>";

    if (!html) {
      body.innerHTML = '<p class="acct-empty">No preferences to show yet.</p>';
      return;
    }
    body.innerHTML = html;

    const noaa = el("acct-pref-noaa");
    if (noaa) {
      noaa.addEventListener("change", async function () {
        const wanted = noaa.checked;
        noaa.disabled = true;
        const res = await api("/api/me/prefs", {
          method: "POST",
          body: JSON.stringify({ noaa_alerts_enabled: wanted }),
        });
        noaa.disabled = false;
        if (!res || !res.ok) noaa.checked = !wanted;
      });
    }
  }

  async function signOutEverywhere(button) {
    if (button) {
      button.disabled = true;
      button.textContent = "Signing out…";
    }
    try {
      await api("/v1/auth/logout-all", { method: "POST" });
    } catch {
      // Clearing local state below still signs this browser out.
    }
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      // Private-mode storage failures are non-fatal.
    }
    window.location.reload();
  }

  function startPanels() {
    if (panelsStarted || !me) return;
    panelsStarted = true;
    loadApps();
    loadSessions();
    loadNotifications();

    const allBtn = el("acct-signout-all");
    if (allBtn && !allBtn.dataset.bound) {
      allBtn.dataset.bound = "1";
      allBtn.addEventListener("click", function () {
        signOutEverywhere(allBtn);
      });
    }
  }

  function applyMe(data) {
    if (!data || typeof data !== "object" || !data.authenticated) return;
    me = data;
    renderHero(data);
    renderMembership(data);
    startPanels();
  }

  window.addEventListener(ME_EVENT, function (ev) {
    applyMe(ev && ev.detail);
  });

  async function boot() {
    await loadApiBase();
    if (me) return;
    // account.js normally broadcasts the payload; fetch it ourselves if that never arrives.
    setTimeout(async function () {
      if (me) return;
      const hasToken = Boolean(localStorage.getItem(TOKEN_KEY));
      const hasCookieSession = document.documentElement.hasAttribute("data-rootrecord-web-auth");
      if (!hasToken && !hasCookieSession) return;
      const res = await getJson("/v1/me");
      if (res.ok) applyMe(res.data);
    }, ME_EVENT_GRACE_MS);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
