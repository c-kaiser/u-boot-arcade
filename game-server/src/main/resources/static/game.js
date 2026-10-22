// U-Boot Arcade – Browser-Client. Keine Abhängigkeiten.
(() => {
  "use strict";

  const WORLD_W = 1600, WORLD_H = 600, CRUSH = 420, OVERHEAT = 9.5;
  const canvas = document.getElementById("sea");
  const ctx = canvas.getContext("2d");
  const $ = (id) => document.getElementById(id);

  let ws = null;
  let myId = null;
  let state = null;          // letzter Spielstand vom Server
  const drawn = new Map();   // geglättete Positionen pro Boot
  const explosions = [];     // {x, depth, t}
  const keys = new Set();
  let statesThisSecond = 0;
  let wantName = null;

  // ---------- Verbindung ----------
  function connect() {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    ws = new WebSocket(`${proto}://${location.host}/ws?client=browser`);
    ws.onopen = () => {
      if (wantName) ws.send(JSON.stringify({ type: "join", name: wantName }));
    };
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.type === "welcome") {
        myId = msg.id;
        $("join").classList.add("hidden");
      } else if (msg.type === "pong") {
        const rtt = performance.now() - msg.t;
        $("g-rtt").textContent = rtt.toFixed(1);
        ws.send(JSON.stringify({ type: "rtt", ms: rtt }));
      } else if (msg.type === "state") {
        statesThisSecond++;
        onState(msg);
      }
    };
    ws.onclose = () => {
      myId = null;
      setTimeout(connect, 2000);
    };
  }

  setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: "ping", t: performance.now() }));
  }, 1000);
  setInterval(() => { $("g-ups").textContent = statesThisSecond; statesThisSecond = 0; }, 1000);

  $("join-form").addEventListener("submit", (e) => {
    e.preventDefault();
    wantName = $("name").value.trim() || "Kapitän";
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: "join", name: wantName }));
    canvas.focus();
  });

  // ---------- Steuerung ----------
  const CONTROL_KEYS = ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "a", "d", "w", "s", " "];
  window.addEventListener("keydown", (e) => {
    if (document.activeElement === $("name")) return;
    if (CONTROL_KEYS.includes(e.key)) { keys.add(e.key); e.preventDefault(); }
  });
  window.addEventListener("keyup", (e) => keys.delete(e.key));
  window.addEventListener("blur", () => keys.clear());

  function currentInput() {
    const left = keys.has("ArrowLeft") || keys.has("a");
    const right = keys.has("ArrowRight") || keys.has("d");
    const up = keys.has("ArrowUp") || keys.has("w");
    const down = keys.has("ArrowDown") || keys.has("s");
    return { type: "input", throttle: (right ? 1 : 0) - (left ? 1 : 0), dive: (down ? 1 : 0) - (up ? 1 : 0), fire: keys.has(" ") };
  }
  setInterval(() => {
    if (myId && ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(currentInput()));
  }, 50);

  // ---------- Spielstand ----------
  function onState(msg) {
    state = msg;
    const names = msg.names || {};
    for (const ev of msg.events) {
      if (ev.type === "DESTROYED") {
        const s = msg.subs.find((x) => x.id === ev.subId);
        if (s) explosions.push({ x: s.x, depth: s.depth, t: performance.now() });
      }
      logEvent(ev, names);
    }
    const ids = new Set(msg.subs.map((s) => s.id));
    for (const id of drawn.keys()) if (!ids.has(id)) drawn.delete(id);

    $("frozen").classList.toggle("hidden", msg.physicsOk);
    $("g-llm").textContent = msg.llmEnabled ? "LLM aktiv" : "Regelbasiert";
    const chaos = $("chaos");
    chaos.innerHTML = "";
    for (const c of msg.chaosActive) { const li = document.createElement("li"); li.textContent = c; chaos.appendChild(li); }

    const me = msg.subs.find((s) => s.id === myId);
    if (me) {
      $("g-depth").textContent = me.depth.toFixed(0);
      $("g-speed").textContent = (Math.abs(me.vx) * 1.944).toFixed(1);
      $("g-pressure").value = me.pressure;
      $("g-pressure-v").textContent = me.pressure.toFixed(1);
      $("g-temp").textContent = me.temp.toFixed(0);
      $("g-hull").value = me.hull;
      $("g-hull-v").textContent = me.hull.toFixed(0);
      $("g-score").textContent = me.score;
      $("g-kills").textContent = me.kills;
      $("dead").classList.toggle("hidden", me.alive);
      if (!me.alive) $("respawn").textContent = `Neues Boot in ${me.respawnIn.toFixed(1)} s`;
    } else {
      $("dead").classList.add("hidden");
    }
  }

  const EVENT_TEXT = {
    HIT: (n, o) => `${n} getroffen von ${o}`,
    KILL: (n, o) => `${n} versenkt ${o}!`,
    DESTROYED: (n, o, d) => d === "torpedo" ? null : `${n} verloren: ${({ overheat: "Kesselexplosion", crush: "zerdrückt", leak: "Leck" })[d] || d}`,
    RESPAWN: () => null,
    OVERHEAT: (n, o, d) => `${n}: Kessel überhitzt (${d})`,
    VALVE_VENT: (n) => `${n}: Sicherheitsventil bläst ab`,
    CRUSH_DEPTH: (n, o, d) => `${n} unter Zerstörungstiefe (${d})`,
    SURFACED: () => null,
  };

  function logEvent(ev, names) {
    const f = EVENT_TEXT[ev.type];
    if (!f) return;
    const text = f(names[ev.subId] || ev.subId, names[ev.otherId] || ev.otherId || "", ev.detail);
    if (!text) return;
    const li = document.createElement("li");
    li.textContent = text;
    li.className = ev.type === "KILL" ? "kill" : (["OVERHEAT", "CRUSH_DEPTH", "VALVE_VENT", "DESTROYED"].includes(ev.type) ? "warn" : "");
    const list = $("events");
    list.prepend(li);
    while (list.children.length > 12) list.lastChild.remove();
  }

  async function refreshLeaderboard() {
    try {
      const rows = await (await fetch("api/leaderboard")).json();
      const ol = $("leaderboard");
      ol.innerHTML = "";
      for (const r of rows) {
        const li = document.createElement("li");
        li.textContent = `${r.name} – ${r.best} `;
        const meta = document.createElement("span");
        meta.textContent = `(${r.kills} Abschüsse, ${r.rounds} Runden)`;
        li.appendChild(meta);
        ol.appendChild(li);
      }
    } catch (e) { /* Leaderboard optional */ }
  }
  setInterval(refreshLeaderboard, 5000);
  refreshLeaderboard();

  // ---------- Zeichnen ----------
  const sx = (x) => x / WORLD_W * canvas.width;
  const sy = (d) => d / WORLD_H * canvas.height;

  function drawSea(now) {
    const g = ctx.createLinearGradient(0, 0, 0, canvas.height);
    g.addColorStop(0, "#1f6fa3");
    g.addColorStop(0.5, "#0d3b63");
    g.addColorStop(1, "#04111f");
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Wellen
    ctx.strokeStyle = "rgba(255,255,255,0.35)";
    ctx.beginPath();
    for (let x = 0; x <= canvas.width; x += 8) {
      const y = 3 + Math.sin(x / 40 + now / 600) * 2;
      x === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Tiefenskala
    ctx.fillStyle = "rgba(255,255,255,0.4)";
    ctx.font = "12px monospace";
    for (let d = 100; d < WORLD_H; d += 100) {
      ctx.fillRect(0, sy(d), 8, 1);
      ctx.fillText(`${d} m`, 12, sy(d) + 4);
    }
    // Zerstörungstiefe
    ctx.strokeStyle = "rgba(255,90,95,0.7)";
    ctx.setLineDash([10, 8]);
    ctx.beginPath(); ctx.moveTo(0, sy(CRUSH)); ctx.lineTo(canvas.width, sy(CRUSH)); ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = "rgba(255,90,95,0.8)";
    ctx.fillText(`Zerstörungstiefe ${CRUSH} m`, canvas.width - 190, sy(CRUSH) - 6);
    // Meeresgrund
    ctx.fillStyle = "#2b2116";
    ctx.fillRect(0, canvas.height - 6, canvas.width, 6);
  }

  function colorFor(s) {
    if (s.id === myId) return "#ffd23f";
    return { PLAYER: "#59d2fe", BOT: "#9aa7b1", ENEMY: "#ff5a5f" }[s.kind] || "#fff";
  }

  function drawSub(s, now) {
    let p = drawn.get(s.id);
    if (!p) { p = { x: s.x, depth: s.depth }; drawn.set(s.id, p); }
    p.x += (s.x - p.x) * 0.35;
    p.depth += (s.depth - p.depth) * 0.35;
    if (!s.alive) return;

    let x = sx(p.x), y = sy(p.depth);
    if (s.belowCrushDepth) { x += (Math.random() - 0.5) * 3; y += (Math.random() - 0.5) * 3; }
    const dir = s.facing;
    const len = 46, h = 13;

    ctx.save();
    ctx.translate(x, y);
    if (s.overheating) {
      ctx.shadowColor = "#ff3b30";
      ctx.shadowBlur = 18;
    }
    ctx.fillStyle = colorFor(s);
    ctx.beginPath();
    ctx.ellipse(0, 0, len / 2, h / 2, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillRect(-6 + dir * 4, -h / 2 - 8, 12, 9);        // Turm
    ctx.fillRect(dir * 2, -h / 2 - 14, 2, 7);               // Periskop
    ctx.fillStyle = "rgba(0,0,0,0.5)";
    ctx.fillRect(-dir * (len / 2 + 4) - 2, -4, 4, 8);       // Schraube
    ctx.restore();

    // Dampfwolken bei Überdruck
    if (s.pressure > OVERHEAT - 0.5) {
      ctx.fillStyle = "rgba(255,255,255,0.5)";
      for (let i = 0; i < 3; i++) {
        const t = ((now / 300) + i / 3) % 1;
        ctx.beginPath();
        ctx.arc(x - dir * 4, y - h - t * 25, 3 + t * 6, 0, Math.PI * 2);
        ctx.fill();
      }
    }

    // Name + Rumpfanzeige
    ctx.fillStyle = "#fff";
    ctx.font = s.id === myId ? "bold 13px monospace" : "12px monospace";
    ctx.textAlign = "center";
    ctx.fillText(s.name, x, y - h - 14);
    ctx.fillStyle = "rgba(0,0,0,0.5)";
    ctx.fillRect(x - 20, y + h, 40, 4);
    ctx.fillStyle = s.hull > 60 ? "#4cd98b" : s.hull > 30 ? "#ffd23f" : "#ff5a5f";
    ctx.fillRect(x - 20, y + h, 40 * s.hull / 100, 4);
    ctx.textAlign = "start";
  }

  function drawTorpedo(t) {
    const x = sx(t.x), y = sy(t.depth);
    const dir = Math.sign(t.vx) || 1;
    ctx.strokeStyle = "rgba(255,255,255,0.3)";
    ctx.beginPath(); ctx.moveTo(x - dir * 30, y); ctx.lineTo(x, y); ctx.stroke();
    ctx.fillStyle = t.ownerId === myId ? "#ffd23f" : "#ff9f1c";
    ctx.fillRect(x - 6, y - 2, 12, 4);
  }

  function drawExplosions(now) {
    for (let i = explosions.length - 1; i >= 0; i--) {
      const e = explosions[i];
      const age = (now - e.t) / 800;
      if (age > 1) { explosions.splice(i, 1); continue; }
      ctx.strokeStyle = `rgba(255,180,60,${1 - age})`;
      ctx.lineWidth = 3;
      ctx.beginPath(); ctx.arc(sx(e.x), sy(e.depth), 10 + age * 50, 0, Math.PI * 2); ctx.stroke();
      ctx.lineWidth = 1;
    }
  }

  function frame(now) {
    drawSea(now);
    if (state) {
      for (const t of state.torpedoes) drawTorpedo(t);
      // eigenes Boot zuletzt, damit es oben liegt
      const subs = [...state.subs].sort((a, b) => (a.id === myId) - (b.id === myId));
      for (const s of subs) drawSub(s, now);
    }
    drawExplosions(now);
    requestAnimationFrame(frame);
  }

  connect();
  requestAnimationFrame(frame);
})();
