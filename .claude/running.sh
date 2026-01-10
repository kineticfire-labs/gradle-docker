#!/usr/bin/env bash
set -euo pipefail

tty_path="$(tty 2>/dev/null || true)"
tty_key="${tty_path#/dev/}"
tty_key="${tty_key//\//_}"
title_file="$HOME/.cache/claude-tab-title/${tty_key}"

mkdir -p "$HOME/.cache/claude-tab-title"
echo "Claude: running" > "$title_file"

# Try to set it immediately too (may be overwritten by Claude output)
printf '\033]0;%s\007' "Claude: running" > /dev/tty 2>/dev/null || true
