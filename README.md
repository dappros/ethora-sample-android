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
buildConfigField("String", "ETHORA_API_BASE_URL", "\"https://api.your-server.com\"")
buildConfigField("String", "ETHORA_XMPP_SERVER_URL", "\"wss://xmpp.your-server.com/ws\"")
buildConfigField("String", "ETHORA_XMPP_HOST", "\"xmpp.your-server.com\"")
buildConfigField("String", "ETHORA_XMPP_CONFERENCE", "\"conference.xmpp.your-server.com\"")
```

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

## Links

- [Ethora SDK documentation](https://github.com/dappros/ethora-sdk-android)
- [Ethora monorepo](https://github.com/dappros/ethora) — all SDKs (Android, iOS, React, WordPress)
- [Setup tool](https://github.com/dappros/ethora-setup) — CLI for account + credential management
