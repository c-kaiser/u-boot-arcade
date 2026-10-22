# arc42agentic

Agenten und Skills zum Schreiben und Reviewen der arc42-Dokumentation in `arc-doc/`.

- Quelle: https://github.com/matthiasnissen/arc42agentic (Commit `2bbef73`)
- Lizenz: CC BY-SA 4.0, siehe [LICENSE.md](LICENSE.md)
- Unverändert übernommen. Aktualisieren: Inhalt aus dem Quell-Repo erneut nach `.agents/` kopieren.

Einbindung:
- **Claude Code:** `.claude/skills/<skill>` sind Symlinks hierher; `.claude/agents/*.md` sind generierte Kopien
  der Agenten (mit `name:`, ohne Copilot-`tools:`).
- **GitHub Copilot (VS Code):** `.github/agents` ist ein Symlink hierher.
- Nach jedem Update `./.agents/sync-claude.sh` ausführen.
- **APM:** alternativ `apm install matthiasnissen/arc42agentic-packages/arc42agenticwrite` (bzw. `…review`).
