package com.ethora.samplechatapp

import android.app.Application
import com.ethora.chat.EthoraChatBootstrap
import com.ethora.chat.EthoraChatSdk

/**
 * Stores process-level flags so MainActivity recreation does not start a second FCM chain.
 *
 * Initializes the Ethora SDK at the earliest possible point in the process and
 * kicks off the SDK's `initBeforeLoad` bootstrap if a JWT is already persisted:
 *   1. `EthoraChatSdk.initialize(this)` — process-singleton DataStore + stores
 *      setup (must run before any Activity/Composable touches `RoomStore`,
 *      `MessageStore`, etc.).
 *   2. `EthoraChatBootstrap.initializeAsync(...)` — opens the XMPP socket,
 *      runs the catch-up flow (`/users/client`, `/chats/my`, history preload)
 *      so `RoomStore.rooms` and the unread badge reflect server state without
 *      the chat tab ever mounting.
 *
 * Both calls are idempotent. Putting them in `Application.onCreate` is
 * deliberate — Android may destroy and recreate an Activity while keeping
 * the process alive, so SDK persistence setup must outlive Activity
 * lifecycle. See the SDK README's "SDK lifecycle" section.
 */
class EthoraApplication : Application() {
    companion object {
        @Volatile
        var fcmRegistrationScheduled: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        try {
            EthoraChatSdk.initialize(this)
            android.util.Log.d("EthoraApplication", "EthoraChatSdk.initialize() done")
        } catch (t: Throwable) {
            android.util.Log.e("EthoraApplication", "SDK initialize failed", t)
        }
        try {
            val session = PlaygroundSessionState.load(this)
            if (session.jwtToken.isNotBlank()) {
                val config = session.toChatConfig()
                EthoraChatBootstrap.initializeAsync(this, config)
                android.util.Log.d("EthoraApplication", "Fired EthoraChatBootstrap.initializeAsync")
            } else {
                android.util.Log.d("EthoraApplication", "No JWT in persisted session — bootstrap deferred")
            }
        } catch (t: Throwable) {
            android.util.Log.e("EthoraApplication", "Early bootstrap failed", t)
        }
    }
}
