#!/usr/bin/env bash
# Capture 20 seconds while interacting with five models on the connected phone.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "$0")/.." && pwd)"
output_dir="$project_dir/artifacts/verification"
mkdir -p "$output_dir"
adb shell perfetto --txt -c - \
    -o /data/misc/perfetto-traces/model-studio.perfetto-trace < "$project_dir/scripts/profile.pbtxt"
adb pull /data/misc/perfetto-traces/model-studio.perfetto-trace "$output_dir/model-studio.perfetto-trace"
adb shell dumpsys meminfo com.iftl.threedee.viewer > "$output_dir/meminfo.txt"
printf 'Saved trace and memory report to %s\n' "$output_dir"
