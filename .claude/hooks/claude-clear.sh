#!/usr/bin/env bash
set -euo pipefail

tty_path="$(tty 2>/dev/null || true)"
tty_key="${tty_path#/dev/}"
tty_key="${tty_key//\//_}"
title_file="$HOME/.cache/claude-tab-title/${tty_key}"

rm -f "$title_file" 2>/dev/null || true
