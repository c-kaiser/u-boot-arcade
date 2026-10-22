// Slop Arcade – Browser-Client. Keine Abhängigkeiten.
(() => {
  "use strict";

  const WORLD_W = 1600, WORLD_H = 600, CRUSH = 420, OVERHEAT = 9.5;
  const canvas = document.getElementById("arena");
  const ctx = canvas.getContext("2d");
  const $ = (id) => document.getElementById(id);

  let ws = null;
  let myId = null;
  let state = null;          // letzter Spielstand vom Server
  const drawn = new Map();   // geglättete Positionen pro Figur
  const explosions = [];     // {x, depth, kind, t}
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
    wantName = $("name").value.trim() || "Mensch";
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
        if (s) explosions.push({ x: s.x, depth: s.depth, kind: s.kind, t: performance.now() });
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
      $("g-depth").textContent = (me.depth / WORLD_H * 100).toFixed(0);
      $("g-speed").textContent = (Math.abs(me.vx) * 3.6).toFixed(1);
      $("g-pressure").value = me.pressure;
      $("g-pressure-v").textContent = me.pressure > OVERHEAT ? "raucht!" : me.pressure > OVERHEAT - 1.5 ? "heiß" : "kühl";
      $("g-hull").value = me.hull;
      $("g-hull-v").textContent = me.hull.toFixed(0);
      $("g-score").textContent = me.score;
      $("g-kills").textContent = me.kills;
      $("dead").classList.toggle("hidden", me.alive);
      if (!me.alive) $("respawn").textContent = `Frischer Kopf in ${me.respawnIn.toFixed(1)} s`;
    } else {
      $("dead").classList.add("hidden");
    }
  }

  // n/kn = Figur des Ereignisses und ihr Typ, o/k = die andere Figur und ihr Typ
  const EVENT_TEXT = {
    HIT: (n, o, d, k) => k === "ENEMY" ? `${n} kriegt Slop von ${o} ab` : `${o} wirft ${n} einen Geistesblitz an den Kopf`,
    KILL: (n, o, d, k, kn) => kn === "ENEMY" ? `${n} begräbt ${o} unter Slop!` : k === "ENEMY" ? `${n} erdet ${o} mit Verstand!` : `${n} überzeugt ${o} mit Argumenten`,
    DESTROYED: (n, o, d) => d === "torpedo" ? null : `${n}: ${({ overheat: "Kopf geplatzt", crush: "im Slop versunken", leak: "Geduld ausgelaufen" })[d] || d}`,
    RESPAWN: () => null,
    OVERHEAT: (n, o, d) => `${n}: Kopf raucht (${d})`,
    VALVE_VENT: (n) => `${n} atmet tief durch`,
    CRUSH_DEPTH: (n, o, d) => `${n} versinkt im Slop (${d})`,
    SURFACED: () => null,
  };

  function logEvent(ev, names) {
    const f = EVENT_TEXT[ev.type];
    if (!f) return;
    const text = f(names[ev.subId] || ev.subId, names[ev.otherId] || ev.otherId || "", ev.detail, kindOf(ev.otherId), kindOf(ev.subId));
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
        meta.textContent = `(${r.kills} Treffer, ${r.rounds} Runden)`;
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

  // Jeder Agent hat seinen eigenen Lieblings-Slop
  const SLOP = ["Synergie!", "Great question!", "Paradigmenwechsel", "Lass uns das delven", "10x 🚀", "Quelle: trust me",
                "Holistisch!", "Game-Changer", "As an AI …", "Lorem ipsum", "Best Practice™", "Disruptiv!", "KI-first"];
  const hash = (str) => [...str].reduce((h, c) => (h * 31 + c.charCodeAt(0)) | 0, 7);
  const kindOf = (id) => state?.subs.find((s) => s.id === id)?.kind;

  function drawArena(now) {
    // oben klarer Kopf, unten Slop
    const g = ctx.createLinearGradient(0, 0, 0, canvas.height);
    g.addColorStop(0, "#e9f4ff");
    g.addColorStop(0.35, "#b9d7ea");
    g.addColorStop(0.7, "#8fa86b");
    g.addColorStop(1, "#4d5a22");
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Slop-Sumpf unterhalb der Grenze, mit Blubberblasen
    const top = sy(CRUSH);
    ctx.fillStyle = "rgba(92, 110, 30, 0.55)";
    ctx.beginPath();
    ctx.moveTo(0, canvas.height);
    for (let x = 0; x <= canvas.width; x += 10) ctx.lineTo(x, top + Math.sin(x / 55 + now / 900) * 5);
    ctx.lineTo(canvas.width, canvas.height);
    ctx.fill();
    ctx.fillStyle = "rgba(200, 220, 120, 0.5)";
    for (let i = 0; i < 14; i++) {
      const t = ((now / 2500) + i / 14) % 1;
      const bx = (hash("b" + i) & 0xffff) / 0xffff * canvas.width;
      ctx.beginPath();
      ctx.arc(bx, canvas.height - t * (canvas.height - top), 2 + (i % 3) * 2, 0, Math.PI * 2);
      ctx.fill();
    }

    // Slop-Skala
    ctx.fillStyle = "rgba(20,30,40,0.55)";
    ctx.font = "12px monospace";
    for (let d = 100; d < WORLD_H; d += 100) {
      ctx.fillRect(0, sy(d), 8, 1);
      ctx.fillText(`${Math.round(d / WORLD_H * 100)} % Slop`, 12, sy(d) + 4);
    }
    ctx.strokeStyle = "rgba(120,60,20,0.7)";
    ctx.setLineDash([10, 8]);
    ctx.beginPath(); ctx.moveTo(0, top); ctx.lineTo(canvas.width, top); ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = "rgba(60,30,10,0.85)";
    ctx.fillText("Ab hier versinkt man im Slop", canvas.width - 230, top - 8);
  }

  function colorFor(s) {
    if (s.id === myId) return "#ffb000";
    return { PLAYER: "#2f8fd8", BOT: "#8a96a3", ENEMY: "#c04cff" }[s.kind] || "#fff";
  }

  // Mensch: Kopf mit Brille, Körper, ein Arm zum Werfen
  function drawHuman(s, dir) {
    ctx.fillStyle = colorFor(s);
    ctx.beginPath(); ctx.roundRect(-9, -2, 18, 20, 6); ctx.fill();            // Körper
    ctx.fillStyle = "#f2c9a0";
    ctx.beginPath(); ctx.arc(0, -11, 10, 0, Math.PI * 2); ctx.fill();         // Kopf
    ctx.strokeStyle = "#222"; ctx.lineWidth = 1.5;
    ctx.beginPath(); ctx.arc(dir * 4, -12, 3, 0, Math.PI * 2); ctx.stroke(); // Brille
    ctx.beginPath(); ctx.arc(dir * 4 - dir * 7, -12, 3, 0, Math.PI * 2); ctx.stroke();
    ctx.strokeStyle = colorFor(s); ctx.lineWidth = 4; ctx.lineCap = "round";
    ctx.beginPath(); ctx.moveTo(dir * 6, 2); ctx.lineTo(dir * 15, -4); ctx.stroke(); // Wurfarm
    ctx.lineWidth = 1; ctx.lineCap = "butt";
  }

  // Agent: Roboterkasten mit Antenne und LED-Augen
  function drawAgent(s, dir, now) {
    ctx.fillStyle = colorFor(s);
    ctx.beginPath(); ctx.roundRect(-14, -16, 28, 30, 6); ctx.fill();
    ctx.strokeStyle = "#333"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(0, -16); ctx.lineTo(0, -25); ctx.stroke();
    ctx.lineWidth = 1;
    ctx.fillStyle = Math.floor(now / 400) % 2 ? "#ff4d4d" : "#ffd23f";
    ctx.beginPath(); ctx.arc(0, -27, 3, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#7dfcff";
    ctx.fillRect(dir * 3 - 4, -9, 6, 5);
    ctx.fillRect(dir * 3 + 5 * dir - 3, -9, 6, 5);
    ctx.fillStyle = "#222";
    ctx.fillRect(-7, 4, 14, 3);                                               // Lautsprecher-Mund
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
    const h = 18;

    ctx.save();
    ctx.translate(x, y);
    if (s.overheating) { ctx.shadowColor = "#ff3b30"; ctx.shadowBlur = 18; }
    if (s.kind === "ENEMY") drawAgent(s, dir, now); else drawHuman(s, dir);
    ctx.restore();

    // Kopf raucht
    if (s.pressure > OVERHEAT - 0.5) {
      ctx.fillStyle = "rgba(80,80,80,0.45)";
      for (let i = 0; i < 3; i++) {
        const t = ((now / 300) + i / 3) % 1;
        ctx.beginPath();
        ctx.arc(x, y - 22 - t * 25, 3 + t * 6, 0, Math.PI * 2);
        ctx.fill();
      }
    }

    // Name + Geduld
    ctx.fillStyle = "#14202b";
    ctx.font = s.id === myId ? "bold 13px monospace" : "12px monospace";
    ctx.textAlign = "center";
    ctx.fillText(s.name, x, y - h - 16);
    ctx.fillStyle = "rgba(0,0,0,0.35)";
    ctx.fillRect(x - 20, y + h, 40, 4);
    ctx.fillStyle = s.hull > 60 ? "#2fb36b" : s.hull > 30 ? "#e0a800" : "#e04848";
    ctx.fillRect(x - 20, y + h, 40 * s.hull / 100, 4);
    ctx.textAlign = "start";
  }

  // Agenten werfen Slop, Menschen werfen Geistesblitze
  function drawProjectile(t, now) {
    const x = sx(t.x), y = sy(t.depth);
    const dir = Math.sign(t.vx) || 1;
    if (kindOf(t.ownerId) === "ENEMY") {
      ctx.fillStyle = "#7a8f1c";
      ctx.beginPath();
      for (let a = 0; a < Math.PI * 2; a += Math.PI / 5) {
        const r = 7 + Math.sin(a * 3 + now / 120) * 2;
        a === 0 ? ctx.moveTo(x + Math.cos(a) * r, y + Math.sin(a) * r) : ctx.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
      }
      ctx.fill();
      ctx.fillRect(x - dir * 14, y + 2, 6, 3);                                 // Tropfen
      const words = SLOP.length;
      ctx.fillStyle = "#3d4a08";
      ctx.font = "11px monospace";
      ctx.textAlign = "center";
      ctx.fillText(SLOP[Math.abs(hash(t.ownerId) + Math.floor(now / 1500)) % words], x, y - 12);
      ctx.textAlign = "start";
    } else {
      ctx.strokeStyle = "rgba(255,210,63,0.5)";
      ctx.beginPath(); ctx.moveTo(x - dir * 26, y); ctx.lineTo(x, y); ctx.stroke();
      ctx.save();
      ctx.shadowColor = "#fff3a0"; ctx.shadowBlur = 12;
      ctx.fillStyle = t.ownerId === myId ? "#ffd23f" : "#fff08a";
      ctx.beginPath(); ctx.arc(x, y - 2, 6, 0, Math.PI * 2); ctx.fill();      // Glühbirne
      ctx.restore();
      ctx.fillStyle = "#888";
      ctx.fillRect(x - 3, y + 3, 6, 4);                                        // Fassung
    }
  }

  function drawExplosions(now) {
    for (let i = explosions.length - 1; i >= 0; i--) {
      const e = explosions[i];
      const age = (now - e.t) / 800;
      if (age > 1) { explosions.splice(i, 1); continue; }
      const x = sx(e.x), y = sy(e.depth);
      if (e.kind === "ENEMY") {
        // Agent geerdet: Funken + Entschuldigung
        ctx.strokeStyle = `rgba(255,220,60,${1 - age})`;
        ctx.lineWidth = 2;
        for (let k = 0; k < 8; k++) {
          const a = k * Math.PI / 4, r = 8 + age * 40;
          ctx.beginPath(); ctx.moveTo(x + Math.cos(a) * r * 0.5, y + Math.sin(a) * r * 0.5);
          ctx.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r); ctx.stroke();
        }
        ctx.fillStyle = `rgba(30,30,30,${1 - age})`;
        ctx.font = "12px monospace"; ctx.textAlign = "center";
        ctx.fillText("Ich entschuldige mich für die Verwirrung.", x, y - 30 - age * 20);
        ctx.textAlign = "start";
      } else {
        // Mensch im Slop versunken: Platsch
        ctx.fillStyle = `rgba(110,130,30,${1 - age})`;
        for (let k = 0; k < 7; k++) {
          const a = -Math.PI + k * Math.PI / 6, r = 6 + age * 45;
          ctx.beginPath(); ctx.arc(x + Math.cos(a) * r, y + Math.sin(a) * r, 5 * (1 - age) + 2, 0, Math.PI * 2); ctx.fill();
        }
      }
      ctx.lineWidth = 1;
    }
  }

  function frame(now) {
    drawArena(now);
    if (state) {
      for (const t of state.torpedoes) drawProjectile(t, now);
      // eigene Figur zuletzt, damit sie oben liegt
      const subs = [...state.subs].sort((a, b) => (a.id === myId) - (b.id === myId));
      for (const s of subs) drawSub(s, now);
    }
    drawExplosions(now);
    requestAnimationFrame(frame);
  }

  connect();
  requestAnimationFrame(frame);
})();
