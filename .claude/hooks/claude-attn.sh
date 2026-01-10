#!/usr/bin/env bash
set -euo pipefail

payload="$(cat)"

ntype="$(jq -r '.notification_type // ""' <<<"$payload")"
msg="$(jq -r '.message // ""' <<<"$payload")"

# Identify the controlling tty so we can target the right tab title file
tty_path="$(tty 2>/dev/null || true)"
tty_key="${tty_path#/dev/}"
tty_key="${tty_key//\//_}"
title_file="$HOME/.cache/claude-tab-title/${tty_key}"

mkdir -p "$HOME/.cache/claude-tab-title"

case "$ntype" in
  permission_prompt)
    echo "Claude: permission" > "$title_file"
    notify-send -u critical "Claude Code" "${msg:-Permission required}"
    ;;
  idle_prompt)
    echo "Claude: waiting" > "$title_file"
    notify-send "Claude Code" "${msg:-Waiting for input}"
    ;;
  *)
    # ignore other notification types
    exit 0
    ;;
esac

# If this hook process has the terminal as /dev/tty, also set title immediately
printf '\033]0;%s\007' "$(cat "$title_file")" > /dev/tty 2>/dev/null || true
