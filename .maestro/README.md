# Maestro flows for `ethora-sample-android`

End-to-end smoke tests that drive the sample app on a real
emulator/device against an Ethora server. Layer 2 of the SDK testing
strategy — see [`ethora-sdk-android` README → Testing](https://github.com/dappros/ethora-sdk-android/blob/main/README.md#testing)
for the split with hermetic Compose UI tests.

## Why Maestro

Compose UI tests in the SDK repo cover composables in isolation —
no XMPP, no API, no FCM. Things that happen *between* those layers
(login round-trip with the right app token, reconnect after airplane
mode, push intent → deep-link to a room, persisted state across app
kill) are exactly where regressions hide. Maestro flows exercise
those paths against `chat-qa.ethora.com`, scripted in YAML so they
double as living documentation of expected behavior.

## Repo layout

```
.maestro/
├── README.md         (you are here)
├── config.yaml       project-level Maestro config
├── assets/           binary fixtures (test images etc.)
│   └── test-image.png   8×8 PNG used by 06-attach-file
├── fixtures/         shared test data (do not commit real credentials)
│   └── test-users.yaml
├── scripts/          helpers invoked by flows or by CI before flows
│   ├── sendAsBob.js     Maestro JS helper — POSTs a message as bob
│   │                    via REST, used by 05-receive-text
│   └── sendPushIntent.sh
│                        adb shell am start helper — invoked by CI
│                        BEFORE 08-push-deep-link to synthesise a
│                        notification-tap intent
└── flows/            one file per scenario, numbered for natural ordering
    ├── 01-login-email.yaml
    ├── 02-login-jwt.yaml
    ├── 03-list-rooms.yaml
    ├── 04-send-text.yaml
    ├── 05-receive-text.yaml      uses scripts/sendAsBob.js
    ├── 06-attach-file.yaml       uses assets/test-image.png seeded
    │                              into /sdcard/Pictures/ by CI
    ├── 07-reconnect-airplane.yaml drives reconnect via the SETUP
    │                              tab's Disconnect button (no adb)
    ├── 08-push-deep-link.yaml    CI runs sendPushIntent.sh first
    ├── 09-logout-relogin.yaml
    └── 10-switch-app.yaml
```

### Why some helpers live outside the flow YAML

Maestro's JS runtime can drive HTTP (`http.post(...)`) but can't
shell out — anything that needs `adb shell` (synthetic intents,
airplane-mode toggles, pushing files into the device gallery) is
invoked from the CI workflow before/after the flow runs. The flow
then asserts on the resulting state.

## Running locally

1. Install Maestro: `curl -fsSL https://get.maestro.mobile.dev | bash`
   (or `brew install maestro`).
2. Build + install the sample on an emulator (API 26+) or a connected
   device:

   ```bash
   ./gradlew :app:installDebug
   ```

3. Populate `.env` at the repo root for the test profile (preferred:
   run `npx @ethora/setup` against your QA app) so the sample can
   connect to a server.
4. Run a single flow:

   ```bash
   maestro test .maestro/flows/01-login-email.yaml
   ```

   Or all flows:

   ```bash
   maestro test .maestro/flows
   ```

## Running in CI

`.github/workflows/maestro.yml` runs the full suite on every push and
on every release tag. CI uses a managed device runner — see the
workflow for the device matrix and result-upload config.

## Authoring a new flow

- Use semantic anchors (`text: "Connect"`) over coordinates — the
  layout will move, the labels won't (unless we localize, in which
  case use `id:` accessibility identifiers).
- Keep each flow under ~30 lines. If you need more, split it.
- Pull credentials from `fixtures/` rather than inlining them.
- Always end with at least one `assertVisible` / `assertNotVisible`
  so a flow that silently no-ops fails loudly.

The 10 flows here cover ~80% of typical usage. If you find a regression
that none of them caught, add a flow for it in the same PR as the fix.
