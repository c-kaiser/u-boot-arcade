#!/usr/bin/env bash
# Bindet die arc42agentic-Agenten und -Skills in Claude Code und GitHub Copilot ein.
# Nach jedem Update von .agents/ erneut ausführen: ./.agents/sync-claude.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# Skills: Claude Code erwartet pro Skill einen Eintrag unter .claude/skills (Symlink erlaubt)
mkdir -p .claude/skills
for dir in .agents/skills/*/; do
  name=$(basename "$dir")
  ln -sfn "../../.agents/skills/$name" ".claude/skills/$name"
done

# Agenten: Claude Code braucht ein name-Feld und versteht die Copilot-Werkzeugnamen nicht.
# Daher Kopien mit name: und ohne tools: (erbt dann alle Werkzeuge).
rm -rf .claude/agents && mkdir -p .claude/agents
for file in .agents/*.agent.md; do
  name=$(basename "$file" .agent.md)
  awk -v name="$name" -v src="$file" '
    NR == 1 && $0 == "---" { print; print "name: " name; next }
    /^tools:/ && !dropped { dropped = 1; next }
    $0 == "---" && !closed { closed = 1; print; print ""; print "<!-- Generiert aus " src " von .agents/sync-claude.sh – nicht von Hand ändern -->"; next }
    { print }' "$file" > ".claude/agents/$name.md"
done

# GitHub Copilot (VS Code) liest Agenten aus .github/agents
mkdir -p .github
ln -sfn ../.agents .github/agents
