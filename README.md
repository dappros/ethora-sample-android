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

[`.maestro/`](.maestro/) holds 10 [Maestro](https://maestro.mobile.dev/)
YAML flows that drive a real emulator/device against `chat-qa.ethora.com`:

1. Email login • 2. JWT login • 3. List rooms • 4. Send text •
5. Receive text • 6. Attach + send file • 7. Reconnect after airplane mode •
8. Push intent → deep-link • 9. Logout / re-login • 10. Switch chat instance

These run on the sample's CI ([`.github/workflows/maestro.yml`](.github/workflows/maestro.yml))
on every push, PR, and SDK release tag — they're the gate that catches
integration regressions like config drift, preset URL breakage, or
cross-platform feature parity gaps.

### Adding a new flow

When a fix lands or a new feature ships, add a Maestro flow in the
same PR. Each flow is ~10–30 lines of YAML; copy
[`flows/01-login-email.yaml`](.maestro/flows/01-login-email.yaml) as a
template. See [`.maestro/README.md`](.maestro/README.md) for authoring
conventions and how to run flows locally.

## Links

- [Ethora SDK documentation](https://github.com/dappros/ethora-sdk-android)
- [Ethora monorepo](https://github.com/dappros/ethora) — all SDKs (Android, iOS, React, WordPress)
- [Setup tool](https://github.com/dappros/ethora-setup) — CLI for account + credential management
