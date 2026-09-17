async function load() {
  const status = await fetchJson("/api/status");
  const players = await fetchJson("/api/players");
  const day = document.getElementById("day-line");
  const online = document.getElementById("online-line");
  const list = document.getElementById("player-list");
  if (status) {
    day.textContent =
      "Day #" +
      status.dayId +
      " · " +
      status.todTicks +
      " ticks (" +
      status.phase +
      ") · " +
      status.lengthMinutes +
      " min/day";
    online.textContent =
      status.online + " online · " + status.afk + " AFK";
  }
  list.innerHTML = "";
  if (players && Array.isArray(players.players)) {
    for (const p of players.players) {
      const li = document.createElement("li");
      li.textContent = p.name + (p.afk ? " (AFK)" : "");
      if (p.afk) li.className = "afk";
      list.appendChild(li);
    }
  }
}

async function fetchJson(path) {
  try {
    const res = await fetch(path);
    if (!res.ok) return null;
    return await res.json();
  } catch (e) {
    return null;
  }
}

load();
setInterval(load, 5000);
