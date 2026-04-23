package com.ethora.samplechatapp

import android.app.Application
import com.ethora.chat.EthoraChatBootstrap

/**
 * Stores process-level flags so MainActivity recreation does not start a second FCM chain.
 *
 * Also kicks off the SDK's `initBeforeLoad` bootstrap here — earliest possible
 * point in the process. By the time the user sees the Setup tab, the SDK has
 * already (in the background):
 *   • POSTed /users/client with the persisted JWT → UserStore
 *   • fetched /chats/my → RoomStore (cache first, then remote)
 *   • opened the XMPP socket and pulled the chatjson private store
 *   • preloaded 30 messages per room
 * so the CHAT-tab unread dot lights up WITHOUT the user ever tapping Chat.
 *
 * Matches option Q14=b. The bootstrap is idempotent and key-cached, so the
 * EthoraChatProvider composable wrapping the UI can call it again without
 * re-connecting — that wrapper is still there as a belt-and-suspenders in
 * case the Application.onCreate path is not yet hit (e.g. process death
 * recreation).
 */
class EthoraApplication : Application() {
    companion object {
        @Volatile
        var fcmRegistrationScheduled: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
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
