#!/usr/bin/env bash
set -euo pipefail

# requires: libnotify-bin

payload="$(cat)"
type="$(jq -r '.notification_type // "notification"' <<<"$payload")"
msg="$(jq -r '.message // "Claude needs you"' <<<"$payload")"
cwd="$(jq -r '.cwd // ""' <<<"$payload")"

notify-send -u critical "Claude Code ($type)" "$msg${cwd:+\n$cwd}"
