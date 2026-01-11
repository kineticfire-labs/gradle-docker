#!/usr/bin/env bash
set -euo pipefail

: "${CLAUDE_TAB_TTY_KEY:?CLAUDE_TAB_TTY_KEY not set}"
: "${CLAUDE_TAB_TTY:?CLAUDE_TAB_TTY not set}"

mkdir -p "$HOME/.cache/claude-tab-title"
title_file="$HOME/.cache/claude-tab-title/${CLAUDE_TAB_TTY_KEY}"

echo "Claude: running" > "$title_file"

# Reassert for ~1.5s so it stays visible even if Claude overwrites it during output
for _ in {1..30}; do
  printf '\033]0;%s\007' "Claude: running" > "$CLAUDE_TAB_TTY" 2>/dev/null || true
  sleep 0.05
done
