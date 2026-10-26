#!/usr/bin/env bash
# Records every FREYA Core state animating via the debug-only CorePreviewActivity.
# Usage (from android/): bash ../.github/scripts/record-core-states.sh ../screenshots/states
OUT="${1:-states}"
mkdir -p "$OUT"
PKG=ai.freya.app.debug
for S in IDLE LISTENING PROCESSING TOOL_EXECUTION SPEAKING AWAITING_APPROVAL ERROR; do
  adb shell am start -W -n $PKG/ai.freya.app.debug.CorePreviewActivity --es state $S > /dev/null || true
  sleep 4
  for N in 1 2 3; do adb exec-out screencap -p > "$OUT/$S-$N.png" || true; sleep 0.7; done
  if [ "$S" = "SPEAKING" ]; then
    adb shell dumpsys gfxinfo $PKG reset > /dev/null || true
    adb shell screenrecord --time-limit 6 --size 540x1200 /sdcard/speaking.mp4 || true
    adb pull /sdcard/speaking.mp4 "$OUT/speaking.mp4" || true
    adb shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo-speaking.txt" || true
  fi
  adb shell am force-stop $PKG || true
done
exit 0
