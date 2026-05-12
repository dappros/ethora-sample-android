# Ethora Sample Android App

A minimal Android chat app demonstrating the [Ethora SDK](https://github.com/dappros/ethora-sdk-android). Open in Android Studio, configure, and run.

## Quick Start

### Option A: Automatic setup (recommended)

```bash
npx @ethora/setup
```

The setup tool creates your Ethora account, provisions test users, and writes config directly into this project.

### Option B: Manual setup

1. Create an account at [ethora.com](https://ethora.com) and create an app
2. Edit `app/build.gradle.kts` — replace the `CHANGE_ME` values with your credentials:

```kotlin
buildConfigField("String", "ETHORA_APP_ID", "\"your-app-id\"")
buildConfigField("String", "ETHORA_APP_TOKEN", "\"your-app-token\"")
buildConfigField("String", "ETHORA_USER_JWT", "\"your-user-jwt\"")
buildConfigField("String", "ETHORA_API_BASE_URL", "\"https://api.your-server.com\"")
buildConfigField("String", "ETHORA_XMPP_SERVER_URL", "\"wss://xmpp.your-server.com/ws\"")
buildConfigField("String", "ETHORA_XMPP_HOST", "\"xmpp.your-server.com\"")
buildConfigField("String", "ETHORA_XMPP_CONFERENCE", "\"conference.xmpp.your-server.com\"")
```

`ETHORA_APP_TOKEN` is required for **Email** auth mode (`/users/login-with-email`).
`ETHORA_USER_JWT` is used by **JWT** auth mode (`/users/client`).

3. Open in Android Studio and run on an emulator (API 26+)

## Requirements

- Android Studio Hedgehog (2023.1) or later
- JDK 17
- Android SDK 34
- Emulator or device running API 26+

## What's Inside

- **`app/`** — Sample app with Material 3 Compose UI, bottom navigation (Home + Chat tabs), unread message badges
- SDK imported via JitPack: `com.github.dappros:ethora-sdk-android:v1.0.19`
- Java 8+ desugaring enabled (required by the SDK)

## Testing

This repo hosts the **Layer 2** end-to-end test flows for the Ethora
Android SDK. Layer 1 (hermetic Compose UI tests) lives in
[`ethora-sdk-android`](https://github.com/dappros/ethora-sdk-android#testing)
alongside the source it exercises.

### What runs here

[`.maestro/`](.maestro/) holds 19 [Maestro](https://maestro.mobile.dev/)
YAML flows that drive a real emulator/device against whichever server
your `.env` / `BuildConfig` points at. **Run them manually** when
shipping an SDK update or when triaging a regression that one of the
flows would catch — the gate against integration regressions like
config drift, preset URL breakage, or cross-platform feature parity
gaps.

The flows are **server-agnostic by design**: they read endpoint
credentials from runtime config, so a developer can point them at
Ethora Cloud QA, a self-hosted instance, or a local stack without
modifying the YAMLs. See [`.maestro/README.md`](.maestro/README.md)
for how to run a single flow, the full suite, and how the `.env` is
populated.

| # | Flow | Covers |
|---|------|--------|
| 01 | login-email | Happy-path email/password login → "Chat ready" |
| 02 | login-jwt | Bring-your-own-auth client-flow JWT |
| 03 | list-rooms | Room list renders post-login with unread counts |
| 04 | send-text | XMPP send round-trip (the most-broken path) |
| 05 | receive-text | MAM delivery from a second user |
| 06 | attach-file | Upload + image bubble |
| 07 | reconnect-airplane | Disconnect → reconnect → history survives |
| 08 | push-deep-link | Notification intent → correct room |
| 09 | logout-relogin | State isolation across sessions |
| 10 | switch-app | Multi-tenant app switcher |
| 11 | login-wrong-password | Negative path surfaces error to UI |
| 13 | message-edit | Long-press → Edit → bubble updates |
| 14 | message-delete | Long-press → Delete → tombstone or removal |
| 15 | message-reaction | Long-press → React → emoji + count visible |
| 16 | create-room | "+" → name → room visible + writable |
| 17 | search-rooms | RoomListView search filter |
| 18 | multi-message-rapid | 5 rapid sends, ordering preserved |
| 19 | room-info | ChatInfoScreen → participants + leave control |
| 20 | offline-pending-resend | Send while disconnected → message lands after reconnect |

(Flow 12 reserved for typing-indicator — needs a `sendAsBob`-style
helper for XMPP composing-state.)

Full coverage table with per-flow assertions and the regression
classes each catches:
[`.maestro/README.md`](.maestro/README.md#coverage-table).

### Adding a new flow

When a fix lands or a new feature ships, add a Maestro flow in the
same PR. Each flow is ~10–30 lines of YAML; copy
[`flows/01-login-email.yaml`](.maestro/flows/01-login-email.yaml) as a
template. See [`.maestro/README.md`](.maestro/README.md) for authoring
conventions and how to run flows locally against your chosen server.

### Cross-platform testing overview

This Android sample's Maestro flows are part of a four-platform
testing stack. The same flow YAMLs (or a near-identical port) run
against iOS too — selectors resolve by accessibility id strings
that match across Android `testTag`, iOS `accessibilityIdentifier`,
and Web `data-testid`.

| Layer 1 (hermetic) | Layer 2 (E2E) |
|--------------------|----------------|
| [`ethora-sdk-android`](https://github.com/dappros/ethora-sdk-android) — Compose UI tests | `ethora-sample-android/.maestro/` — 19 flows (this repo) |
| [`ethora-sdk-swift`](https://github.com/dappros/ethora-sdk-swift) — XCTest + `accessibilityIdentifier` markers | [`ethora-sample-swift/.maestro/`](https://github.com/dappros/ethora-sample-swift) — same 19 flows on iOS Simulator |
| [`ethora-chat-component`](https://github.com/dappros/ethora-chat-component) — Vitest + RTL + `data-testid` | [`ethora-app-reactjs/tests/e2e/`](https://github.com/dappros/ethora-app-reactjs) — Playwright |

A Maestro `id: "chat_input"` resolves the same intent on Android +
iOS. A Playwright `[data-testid="chat_input"]` resolves it when
`<Chat>` mounts in the host. Selectors are 4-repo-coupled — keep
them in sync.

## Links

- [Ethora SDK documentation](https://github.com/dappros/ethora-sdk-android)
- [Ethora monorepo](https://github.com/dappros/ethora) — all SDKs (Android, iOS, React, WordPress)
- [Setup tool](https://github.com/dappros/ethora-setup) — CLI for account + credential management
