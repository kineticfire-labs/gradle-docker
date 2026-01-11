#!/usr/bin/env bash
set -euo pipefail

payload="$(cat)"

: "${CLAUDE_TAB_TTY_KEY:?CLAUDE_TAB_TTY_KEY not set}"
: "${CLAUDE_TAB_TTY:?CLAUDE_TAB_TTY not set}"

ntype="$(jq -r '.notification_type // ""' <<<"$payload")"
msg="$(jq -r '.message // ""' <<<"$payload")"

title_file="$HOME/.cache/claude-tab-title/${CLAUDE_TAB_TTY_KEY}"
mkdir -p "$HOME/.cache/claude-tab-title"

case "$ntype" in
  permission_prompt)
    echo "Claude: permission" > "$title_file"
    notify-send -u critical "Claude Code" "${msg:-Permission required}"
    printf '\033]0;%s\007' "Claude: permission" > "$CLAUDE_TAB_TTY" 2>/dev/null || true
    ;;
  idle_prompt)
    echo "Claude: waiting" > "$title_file"
    notify-send -u critical "Claude Code" "${msg:-Waiting for input}"
    printf '\033]0;%s\007' "Claude: waiting" > "$CLAUDE_TAB_TTY" 2>/dev/null || true
    ;;
  *)
    exit 0
    ;;
esac
