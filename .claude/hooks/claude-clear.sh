#!/usr/bin/env bash
set -euo pipefail

: "${CLAUDE_TAB_TTY_KEY:?CLAUDE_TAB_TTY_KEY not set (export it in ~/.bashrc)}"
: "${CLAUDE_TAB_TTY:?CLAUDE_TAB_TTY not set (export it in ~/.bashrc)}"

title_file="$HOME/.cache/claude-tab-title/${CLAUDE_TAB_TTY_KEY}"

# Remove custom override so your shell falls back to directory title
rm -f "$title_file" 2>/dev/null || true

# Immediately set title to the directory name of the terminal session (best-effort).
# This may be overwritten until the next prompt redraw; PROMPT_COMMAND=tab_title will reapply.
default_title="$(basename "$PWD")"
printf '\033]0;%s\007' "$default_title" > "$CLAUDE_TAB_TTY" 2>/dev/null || true
