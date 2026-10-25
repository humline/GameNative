#!/usr/bin/env bash
set -euo pipefail

image="gamenative-android-builder:local"
container="gamenative-apk-export"

nerdctl build --file Dockerfile --tag "$image" .
nerdctl rm --force "$container" >/dev/null 2>&1 || true
nerdctl create --name "$container" "$image" >/dev/null
nerdctl cp "$container:/workspace/app/build/outputs/apk/modern/debug/app-modern-debug.apk" ./GameNative-modern-debug.apk
nerdctl rm "$container" >/dev/null
