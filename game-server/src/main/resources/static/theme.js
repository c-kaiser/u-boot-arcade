// Darstellungsvarianten (U-Boot, Slop). Rein clientseitig: Server, Spielmechanik und Telemetrie bleiben gleich.
// Auswahl per ?theme=slop oder Umschalter im Kopf, gemerkt im localStorage.
(() => {
  "use strict";

  const THEMES = {
    uboot: {
      label: "U-Boot",
      texts: {
        title: "U-Boot Arcade",
        tagline: "",
        joinTitle: "Bereit zum Tauchen?",
        joinButton: "Abtauchen!",
        hint: "← → Fahrt &nbsp;·&nbsp; ↑ ↓ Tauchen &nbsp;·&nbsp; Leertaste Torpedo",
        dead: "Versenkt!",
        panelSelf: "Dein Boot",
        depth: "Tiefe", depthUnit: "m",
        speed: "Fahrt", speedUnit: "kn",
        pressure: "Dampfdruck", pressureUnit: "bar",
        hull: "Rumpf",
        kills: "Abschüsse",
        panelEvents: "Funkspruch",
        // Admin
        crew: "Besatzung",
        bots: "Server-Bots<small>Mehr Boote = mehr Last für die Physik</small>",
        enemies: "Gegner (Gegner-KI)<small>Werden jede Sekunde über die Gegner-KI gesteuert</small>",
        llm: "LLM für Gegner verwenden<small>Über LiteLLM; sonst regelbasierter Fallback</small>",
        aiLatency: "Gegner-KI-Latenz<small>Gegner reagieren träge (ms)</small>",
        aiRunaway: "Gegner-KI-Endlosschleife<small>LLM wird pro Entscheidung 8x gefragt – Tokens und Kosten steigen</small>",
        boiler: "Kesselkühlung defekt<small>Dampfdruck steigt über 9,5 bar – Überhitzung</small>",
        leak: "Leck im Rumpf<small>Alle Boote sinken und verlieren Rumpf</small>",
        current: "Meeresströmung<small>Treibt alle Boote zur Seite</small>",
      },
      defaultName: "Kapitän",
      respawn: (s) => `Neues Boot in ${s} s`,
      names: {},
      chaos: [],
    },
    slop: {
      label: "Slop",
      texts: {
        title: "Slop Arcade",
        tagline: "KI-Agenten werfen mit Slop. Du wirfst mit Verstand.",
        joinTitle: "Bereit, mitzudenken?",
        joinButton: "Los geht’s!",
        hint: "← → laufen &nbsp;·&nbsp; ↑ ↓ hoch/runter &nbsp;·&nbsp; Leertaste Geistesblitz werfen",
        dead: "Im Slop versunken!",
        panelSelf: "Dein Kopf",
        depth: "Slop-Pegel", depthUnit: "%",
        speed: "Tempo", speedUnit: "km/h",
        pressure: "Denkdruck", pressureUnit: "",
        hull: "Geduld",
        kills: "Treffer",
        panelEvents: "Flurfunk",
        crew: "Belegschaft",
        bots: "Kolleg:innen (Server-Bots)<small>Mehr Figuren = mehr Last für die Physik</small>",
        enemies: "KI-Agenten (Gegner-KI)<small>Werden jede Sekunde über die Gegner-KI gesteuert</small>",
        llm: "LLM für Agenten verwenden<small>Über LiteLLM; sonst regelbasierter Fallback</small>",
        aiLatency: "Gegner-KI-Latenz<small>Agenten reagieren träge (ms)</small>",
        aiRunaway: "Agent denkt nach (Endlosschleife)<small>LLM wird pro Entscheidung 8x gefragt – Tokens, Kosten und Slop steigen</small>",
        boiler: "Kaffeemaschine kaputt<small>Denkdruck steigt über die Grenze – Köpfe rauchen</small>",
        leak: "Benachrichtigungsflut<small>Alle sinken in den Slop und verlieren Geduld</small>",
        current: "Hype-Welle<small>Treibt alle zur Seite</small>",
      },
      defaultName: "Mensch",
      respawn: (s) => `Frischer Kopf in ${s} s`,
      // Der Server vergibt die U-Boot-Namen; hier nur die Anzeige
      names: { Kraken: "SlopGPT", Leviathan: "Halluzinator", Nautilus: "Buzzwordinator", Moby: "Copypasta", Nessie: "Promptimus" },
      // Chaos-Bezeichnungen vom Server (Präfix) → Anzeige
      chaos: [
        ["Kesselkühlung defekt", "Kaffeemaschine kaputt"],
        ["Leck im Rumpf", "Benachrichtigungsflut"],
        ["Strömung", "Hype-Welle"],
        ["KI-Endlosschleife", "Agent denkt nach (Endlosschleife)"],
      ],
    },
  };

  function initial() {
    const fromUrl = new URLSearchParams(location.search).get("theme");
    if (THEMES[fromUrl]) return fromUrl;
    try { const saved = localStorage.getItem("theme"); if (THEMES[saved]) return saved; } catch (e) { /* ohne Speicher */ }
    return "uboot";
  }

  let id = initial();
  const listeners = [];

  function apply() {
    const th = THEMES[id];
    document.documentElement.dataset.theme = id;
    for (const el of document.querySelectorAll("[data-t]")) el.innerHTML = th.texts[el.dataset.t] ?? "";
    const titleSuffix = document.documentElement.dataset.titleSuffix || "";
    document.title = th.texts.title + titleSuffix;
    const toggle = document.getElementById("theme-toggle");
    if (toggle) toggle.textContent = `Variante: ${th.label}`;
    for (const f of listeners) f(id);
  }

  window.Theme = {
    get id() { return id; },
    get current() { return THEMES[id]; },
    t: (key) => THEMES[id].texts[key],
    name: (n) => {
      const mapped = THEMES[id].names[n];
      if (mapped) return mapped;
      return id === "slop" && /^Bot-\d+$/.test(n) ? n.replace("Bot-", "Kolleg:in ") : n;
    },
    chaos: (label) => {
      const hit = THEMES[id].chaos.find(([from]) => label.startsWith(from));
      return hit ? hit[1] + label.slice(hit[0].length) : label;
    },
    onChange: (f) => listeners.push(f),
    toggle: () => {
      id = id === "uboot" ? "slop" : "uboot";
      try { localStorage.setItem("theme", id); } catch (e) { /* ohne Speicher */ }
      apply();
    },
  };

  document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("theme-toggle")?.addEventListener("click", () => window.Theme.toggle());
    apply();
  });
})();
