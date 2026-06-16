#!/usr/bin/env bash
# Synthesises a notification-tap intent for the sample app, mimicking
# what the SDK's PushNotificationManager does when the user taps an
# FCM notification. Used by `flows/08-push-deep-link.yaml`.
#
# Maestro's JS runtime can't `adb shell`, so this script is invoked
# from the CI workflow (`.github/workflows/maestro.yml`) **before**
# Maestro runs the flow — the flow then asserts on the launched
# state. For local runs:
#
#     ./sendPushIntent.sh <room-jid>   then   maestro test flows/08-push-deep-link.yaml
#
# Required env / args:
#   $1  room JID, e.g.  abc123_def456@conference.xmpp.chat-qa.ethora.com
#
# Optional env:
#   ADB_SERIAL  device serial (when more than one is attached)

set -euo pipefail

JID="${1:-${JID:-}}"
if [[ -z "$JID" ]]; then
  echo "sendPushIntent.sh: missing room JID. Usage: $0 <room-jid>" >&2
  exit 1
fi

ADB="adb"
if [[ -n "${ADB_SERIAL:-}" ]]; then
  ADB="adb -s ${ADB_SERIAL}"
fi

# Component must match the launcher activity declared in the sample's
# AndroidManifest. Update if the manifest changes.
COMPONENT="com.ethora/com.ethora.samplechatapp.MainActivity"

$ADB shell am start \
  -n "$COMPONENT" \
  --es notification_jid "$JID"
