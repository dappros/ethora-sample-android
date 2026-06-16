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
├── scripts/          helpers invoked by flows or run manually before flows
│   ├── sendAsBob.js     Maestro JS helper — POSTs a message as bob
│   │                    via REST, used by 05-receive-text
│   └── sendPushIntent.sh
│                        adb shell am start helper — run BEFORE
│                        08-push-deep-link to synthesise a
│                        notification-tap intent
└── flows/            one file per scenario, numbered for natural ordering
    ├── 01-login-email.yaml
    ├── 02-login-jwt.yaml
    ├── 03-list-rooms.yaml
    ├── 04-send-text.yaml
    ├── 05-receive-text.yaml      uses scripts/sendAsBob.js
    ├── 06-attach-file.yaml       uses assets/test-image.png; before
    │                              running, push it into the device's
    │                              Pictures dir via `adb push`
    ├── 07-reconnect-airplane.yaml drives reconnect via the SETUP
    │                              tab's Disconnect button (no adb)
    ├── 08-push-deep-link.yaml    run scripts/sendPushIntent.sh first
    ├── 09-logout-relogin.yaml
    ├── 10-switch-app.yaml
    ├── 11-login-wrong-password.yaml  negative login path
    ├── 13-message-edit.yaml          long-press → Edit → save
    ├── 14-message-delete.yaml        long-press → Delete → tombstone
    ├── 15-message-reaction.yaml      long-press → React → emoji
    ├── 16-create-room.yaml           "+" → Create → assert in list
    ├── 17-search-rooms.yaml          RoomListView search filter
    ├── 18-multi-message-rapid.yaml   rapid-fire 5 sends, ordering
    ├── 19-room-info.yaml             ChatInfoScreen → participants
    └── 20-offline-pending-resend.yaml disconnect → send → reconnect
```

(Flow 12 reserved for typing-indicator — needs a second-user helper
that pokes XMPP composing-state on bob's behalf, similar in shape to
`sendAsBob.js` but talking to a typing endpoint.)

## Coverage table

What each flow proves end-to-end against `chat-qa.ethora.com`.

| # | Flow | Asserts | Catches |
|---|------|---------|---------|
| 01 | `login-email` | Email + password → `Chat ready: Yes` | Wrong app token, broken `/users/login-with-email` shape, expired refresh tokens |
| 02 | `login-jwt` | Custom JWT → `/users/client` accepts | `TOKEN_WRONG_TYPE`, missing JWTLoginConfig wiring |
| 03 | `list-rooms` | After login, room list renders | `GET /chats/my` regression, unread-count desync |
| 04 | `send-text` | Send round-trips and renders the bubble | XMPP BIND-result match, ConnectionStore stuck CONNECTING, send button gating |
| 05 | `receive-text` | Bob's REST-sent message arrives via XMPP | MAM subscription missing, XMPPClient duplication |
| 06 | `attach-file` | Pick from gallery → upload → image bubble | Upload 401 (wrong auth), MIME rejection, chunked-upload UI break |
| 07 | `reconnect-airplane` | Disconnect → Connect → history survives | XMPP client not torn down, status banner stuck OFFLINE, remount-on-reconnect wipe |
| 08 | `push-deep-link` | Synthetic notification intent → right room | Intent extras lost, room JID URL-decoded wrong, navigation before bootstrap |
| 09 | `logout-relogin` | Full logout → re-login same user → state isolated | Persisted state leaking across sessions, DataStore corruption |
| 10 | `switch-app` | App A → App B in-process | Store not flushed, XMPP client persisting wrong-app JID |
| 11 | `login-wrong-password` | 401 surfaces as "Login failed", form remains editable | Error suppressed, retry loop, accidental login under stale token |
| 13 | `message-edit` | Long-press → Edit → bubble updates in place | `editText` prop not flowing, edit calling POST instead of PUT, optimistic-update reconciliation |
| 14 | `message-delete` | Long-press → Delete → bubble gone or tombstoned | Delete RPC silently failing, MAM still returning deleted, isDeleted not surfaced |
| 15 | `message-reaction` | Long-press → React → emoji + count visible | Reaction not stored in `Message.reaction`, picker missing presets, count not incrementing |
| 16 | `create-room` | "+" → Create dialog → new room visible + writable | Create-room RPC silent failure, JID collision on duplicate name, presence not joined for new room |
| 17 | `search-rooms` | RoomListView SearchBar narrows + restores list | Predicate not case-insensitive, list not re-rendering on query change |
| 18 | `multi-message-rapid` | 5 back-to-back sends all visible in order | Out-of-order ack reorder, optimistic UI dropping bubbles, ChatInput debounce eating taps |
| 19 | `room-info` | ChatInfoScreen → participants + leave control | `GET /chats/:jid/details` regression, avatar URL malformed, leave-room hidden |
| 20 | `offline-pending-resend` | Disconnect → send → reconnect → message lands | Send dropped silently, sendFailed never clearing, retry double-sends, pending bubble stuck |

Several flows depend on **shared selectors** that don't yet exist
in source (\`id: "chat_input"\`, \`id: "chat_send_button"\`,
\`id: "rooms_search_input"\`, etc.). Marked \`optional: true\` so
flows still execute, but they assert nothing useful until those
\`testTag\`s land in the SDK's Compose tree. Adding semantic
testTags to the chat-ui composables is the highest-leverage source
change to unblock these.

### Why some helpers live outside the flow YAML

Maestro's JS runtime can drive HTTP (`http.post(...)`) but can't
shell out — anything that needs `adb shell` (synthetic intents,
airplane-mode toggles, pushing files into the device gallery) runs
as a separate command before/after the flow. The flow then asserts
on the resulting state. The helper scripts in `scripts/` are the
canonical place for these — invoke them manually before launching
the affected flow.

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

## Pointing at a non-default server

The flows read endpoint config from the runtime environment that the
sample app's Setup tab is configured with — they never hardcode a
server. To run against a different backend (a self-hosted instance,
local stack, or a different Cloud app), generate the `.env` for that
server via `npx @ethora/setup` and rebuild the sample. The flows will
follow.

This is intentional: tests must not assume any particular server is
reachable from a developer's machine. The same applies to credentials
— treat the test users in `fixtures/test-users.yaml` as templates,
not commitments. Substitute the values your QA instance accepts.

## CI (optional, not provided here)

This repo intentionally does **not** ship a Maestro CI workflow.
Reasons:

- CI would need to commit to a single server target (credentials in
  repo secrets), which conflicts with the server-agnostic design
  above.
- E2E flows against a live backend are noisy in CI when the backend
  evolves on its own cadence. The team prefers running them
  manually against the server relevant to the change being shipped.

If you want CI, fork the repo and add your own workflow targeting
the server credentials your fork is allowed to use.

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
