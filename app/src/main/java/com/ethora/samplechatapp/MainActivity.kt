package com.ethora.samplechatapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethora.chat.Chat
import com.ethora.chat.core.ChatService
import com.ethora.chat.core.config.AppConfig
import com.ethora.chat.core.config.ChatConfig
import com.ethora.chat.core.config.ChatHeaderSettingsConfig
import com.ethora.chat.core.config.JWTLoginConfig
import com.ethora.chat.core.config.XMPPSettings
import com.ethora.chat.core.networking.ApiClient
import com.ethora.chat.core.store.ChatStore
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import com.ethora.chat.core.store.RoomStore
import com.ethora.chat.core.store.MessageStore
import com.ethora.chat.core.models.Message
import com.ethora.chat.core.models.Room
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ethora.chat.core.push.PushNotificationManager
import com.ethora.chat.core.store.LogStore
import com.ethora.chat.core.store.UserStore
import com.ethora.chat.ui.components.LogsView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ethora.chat.EthoraChatBootstrap
import androidx.compose.material3.TextButton
class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFirebaseAvailable: Boolean = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "POST_NOTIFICATIONS permission granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LogStore.startNewSession("sample app start")
        // SDK persistence + stores init lives in EthoraApplication.onCreate so it
        // is process-scoped, not Activity-scoped — Activity recreation must not
        // re-run DataStore setup. See SDK README "SDK lifecycle" section.
        logSigningCertificateSha1()
        isFirebaseAvailable = checkFirebaseInit()
        if (isFirebaseAvailable) {
            logGooglePlayServicesStatus()
            logFirebaseInstallationId()
        } else {
            Log.w(TAG, "Firebase is not configured (no google-services.json). Skip FCM init.")
        }
        // Push-related startup is paused while SDK push subscription is off
        // (see SDK_PUSH_SUBSCRIBE_ENABLED in EthoraChat.kt). Flip both back on
        // together to re-enable: uncomment these calls AND the SDK const.
        // requestNotificationPermission()
        // if (isFirebaseAvailable) {
        //     scheduleFcmTokenFetchOnce()
        // }
        handleNotificationIntent(intent)

        setContent {
            SampleChatApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val jid = intent?.getStringExtra("notification_jid")
        if (jid != null) {
            Log.d(TAG, "Opened from push notification, jid=$jid")
            PushNotificationManager.setPendingNotificationJid(jid)
            intent.removeExtra("notification_jid")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkFirebaseInit(): Boolean {
        try {
            val app = FirebaseApp.getInstance()
            Log.d(TAG, "Firebase OK: project=${app.options.projectId}, appId=${app.options.applicationId}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase NOT initialized!", e)
            return false
        }
    }

    private fun logGooglePlayServicesStatus() {
        val api = GoogleApiAvailability.getInstance()
        val result = api.isGooglePlayServicesAvailable(this)
        if (result == ConnectionResult.SUCCESS) {
            Log.d(TAG, "Google Play services: OK")
        } else {
            Log.e(TAG, "Google Play services: code=$result ${api.getErrorString(result)}")
        }
    }

    private fun logFirebaseInstallationId() {
        try {
            FirebaseInstallations.getInstance().id
                .addOnSuccessListener { fid -> Log.d(TAG, "Firebase Installation ID OK: ${fid.take(12)}...") }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firebase Installation ID FAILED (same layer as FCM token)", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Installation ID skipped: Firebase not initialized", e)
        }
    }

    private fun logSigningCertificateSha1() {
        try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val si = info.signingInfo
                    ?: error("signingInfo was null on API ${Build.VERSION.SDK_INT}")
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                info.signatures!!
            }
            val md = MessageDigest.getInstance("SHA1")
            val sha1 = md.digest(signatures[0].toByteArray())
                .joinToString(":") { b -> "%02X".format(b) }
            Log.w(TAG, "APK signing SHA-1 (verify in Firebase -> com.ethora): $sha1")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read APK signing SHA-1", e)
        }
    }

    private fun scheduleFcmTokenFetchOnce() {
        if (EthoraApplication.fcmRegistrationScheduled) return
        synchronized(EthoraApplication::class.java) {
            if (EthoraApplication.fcmRegistrationScheduled) return
            EthoraApplication.fcmRegistrationScheduled = true
        }
        fetchFcmTokenWithRetry(attempt = 1)
    }

    private fun fetchFcmTokenWithRetry(attempt: Int) {
        val maxAttempts = 6
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "FCM token OK (attempt $attempt): ${token.take(20)}...")
                    PushNotificationManager.setFcmToken(token)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "FCM attempt $attempt failed: ${e.message}")
                    if (attempt < maxAttempts) {
                        val delayMs = when (attempt) {
                            1 -> 2_000L
                            2 -> 5_000L
                            else -> 10_000L
                        }
                        mainHandler.postDelayed({ fetchFcmTokenWithRetry(attempt + 1) }, delayMs)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseMessaging.getInstance() crashed", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleChatApp() {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val session = remember { PlaygroundSessionState.load(context) }
    val rooms by RoomStore.rooms.collectAsState()
    val hasUnread by EthoraChatBootstrap.hasUnread().collectAsState(initial = false)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var networkCut by remember { mutableStateOf(false) }

    // Snapshot of the current ChatConfig — initBeforeLoad fires when the user
    // opens the Setup tab and configures a JWT. The config is recomputed on
    // each session mutation so new JWT tokens immediately kick a bootstrap.
    val chatConfig = session.toChatConfig()

    // First-launch build stamp so every pasted log dump identifies the
    // exact build. Format: "<short sha> @ <YY.MM.DD.HH:mm UTC> on <branch>".
    // The SDK version is the JitPack coordinate pinned in app/build.gradle.kts.
    LaunchedEffect(Unit) {
        LogStore.info(
            "Playground",
            "sample-chat-app build=${BuildConfig.SAMPLE_GIT_SHA} " +
                "@${BuildConfig.SAMPLE_BUILD_TIME}UTC " +
                "branch=${BuildConfig.SAMPLE_GIT_BRANCH}",
            category = "sample-ui"
        )
    }

    LaunchedEffect(rooms.size) {
        LogStore.info("Playground", "Rooms updated: ${rooms.size}", category = "sample-ui")
    }

    // Wrapping the app in EthoraChatProvider triggers the SDK's background
    // bootstrap (JWT login → /chats/my → XMPP connect → private-store sync →
    // 20-msg preload per room) BEFORE the user taps into the chat UI, so the
    // unread dot on the CHAT tab reflects the real server state immediately.
    com.ethora.chat.EthoraChatProvider(config = chatConfig) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (networkCut) "Offline (simulated)" else "Online",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (networkCut) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch {
                                    if (!networkCut) {
                                        EthoraChatBootstrap.shutdownBlocking()
                                        LogStore.warning("Playground", "Network cut simulated — XMPP disconnected", category = "sample-ui")
                                    } else {
                                        EthoraChatBootstrap.initializeAsync(context, chatConfig)
                                        LogStore.info("Playground", "Network restore simulated — reconnecting", category = "sample-ui")
                                    }
                                    networkCut = !networkCut
                                }
                            }) {
                                Text(if (networkCut) "Restore" else "Cut network")
                            }
                        }
                    }
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Setup") },
                            label = { Text("SETUP") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                if (hasUnread) {
                                    // Badge with no content renders as a solid dot —
                                    // matches the boolean has-unread API.
                                    BadgedBox(badge = { Badge() }) {
                                        Icon(Icons.Default.Email, contentDescription = "Chat")
                                    }
                                } else {
                                    Icon(Icons.Default.Email, contentDescription = "Chat")
                                }
                            },
                            label = { Text("CHAT") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Inbox, contentDescription = "Testing") },
                            label = { Text("TESTING") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Text("L") },
                            label = { Text("LOGS") }
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    when (selectedTab) {
                        0 -> SetupTab(
                            context = context,
                            session = session,
                            onConnect = {
                                scope.launchConnection(context, session)
                            },
                            onDisconnect = {
                                ChatService.logout.performLogout()
                                session.isConnected = false
                                session.lastError = null
                                LogStore.warning("Playground", "Disconnected. Logout performed and session cleared.", category = "sample-ui")
                                PlaygroundSessionState.save(context, session)
                            }
                        )
                        1 -> ChatTab(session = session)
                        2 -> TestingTab(session = session)
                        else -> LogsTab()
                    }
                }
            }
        }
    }
    } // EthoraChatProvider
}

private fun CoroutineScope.launchConnection(
    context: Context,
    session: PlaygroundSessionState
) = launch {
    session.isBusy = true
    session.lastError = null
    try {
        LogStore.info("Playground", "Connect requested")
        LogStore.info(
            "Playground",
            "HTTP base=${session.baseUrl}, XMPP ws=${session.xmppWebSocketUrl}, host=${session.xmppHost}, conference=${session.xmppConference}"
        )
        val config = session.toChatConfig()
        if (session.xmppConference != session.normalizedConferenceDomain()) {
            val fixed = session.normalizedConferenceDomain()
            LogStore.warning("Playground", "Normalized XMPP conference domain to $fixed", category = "sample-ui")
            session.xmppConference = fixed
        }
        ChatStore.setConfig(config)
        ApiClient.setBaseUrl(config.baseUrl ?: AppConfig.defaultBaseURL, config.customAppToken)
        when (session.authMode) {
            AuthMode.JWT_CUSTOM -> {
                if (session.jwtToken.isBlank()) error("JWT token is required.")
                LogStore.info("Playground", "Auth: login via JWT", category = "auth")
                val response = com.ethora.chat.core.networking.AuthAPIHelper.loginViaJWT(
                    token = session.jwtToken,
                    baseUrl = session.baseUrl
                ) ?: error("JWT login failed.")
                UserStore.setUser(response)
                LogStore.success("Playground", "HTTP login success (JWT)", category = "auth")
            }
            AuthMode.EMAIL_PASSWORD -> {
                if (session.email.isBlank() || session.password.isBlank()) {
                    error("Email and password are required.")
                }
                if (session.appToken.isBlank()) {
                    error("App token is required for email login. Set ETHORA_APP_TOKEN or fill App token in Setup.")
                }
                LogStore.info("Playground", "Auth: login with email", category = "auth")
                val response = com.ethora.chat.core.networking.AuthAPIHelper.loginWithEmail(
                    email = session.email,
                    password = session.password,
                    baseUrl = session.baseUrl
                )
                UserStore.setUser(response)
                LogStore.success("Playground", "HTTP login success (email)", category = "auth")
            }
        }
        val xmppUser = UserStore.currentUser.value?.xmppUsername
        val xmppPass = UserStore.currentUser.value?.xmppPassword
        if (xmppUser.isNullOrBlank() || xmppPass.isNullOrBlank()) {
            LogStore.warning("Playground", "XMPP credentials are empty in login response. Messages will not load.", category = "auth")
        } else {
            LogStore.info("Playground", "XMPP credentials present for user=$xmppUser", category = "auth")
        }
        session.isConnected = true
        PlaygroundSessionState.save(context, session)
    } catch (e: Exception) {
        session.lastError = e.message ?: "Connection failed"
        LogStore.error("Playground", "Connect failed: ${session.lastError}", category = "auth")
    } finally {
        session.isBusy = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupTab(
    context: Context,
    session: PlaygroundSessionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var jsonMode by remember { mutableStateOf(false) }
    var jsonValue by remember { mutableStateOf(session.toJson()) }
    var authExpanded by remember { mutableStateOf(true) }
    var uiExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = !jsonMode,
                    onClick = { jsonMode = false },
                    label = { Text("Fields") }
                )
                androidx.compose.material3.FilterChip(
                    selected = jsonMode,
                    onClick = { jsonMode = true },
                    label = { Text("JSON") }
                )
            }
            Spacer(Modifier.padding(top = 6.dp))
            if (!jsonMode) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeader("Authorization", authExpanded) { authExpanded = !authExpanded }
                                if (authExpanded) {
                                    SetupFields(session)
                                }
                            }
                        }
                    }
                    item {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeader("UI settings", uiExpanded) { uiExpanded = !uiExpanded }
                                if (uiExpanded) {
                                    UISettingsFields(session)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = jsonValue,
                        onValueChange = { jsonValue = it },
                        label = { Text("Setup JSON") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        minLines = 10
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Button(onClick = {
                            kotlin.runCatching {
                                jsonValue = PlaygroundSessionState.prettyJson(jsonValue)
                                LogStore.success("Playground", "JSON formatted", category = "sample-ui")
                            }.onFailure { LogStore.error("Playground", "JSON error: ${it.message}", category = "sample-ui") }
                        }) { Text("Format JSON") }
                        androidx.compose.material3.Button(onClick = {
                            kotlin.runCatching {
                                session.applyJson(jsonValue)
                                jsonValue = session.toJson()
                                LogStore.success("Playground", "JSON applied", category = "sample-ui")
                                PlaygroundSessionState.save(context, session)
                            }.onFailure { LogStore.error("Playground", "JSON error: ${it.message}", category = "sample-ui") }
                        }) { Text("Apply JSON") }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(
                    onClick = onConnect,
                    enabled = !session.isBusy
                ) { Text(if (session.isBusy) "Connecting..." else "Connect") }
                androidx.compose.material3.OutlinedButton(
                    onClick = onDisconnect,
                    enabled = !session.isBusy
                ) { Text("Disconnect") }
            }
            // Manual triggers for the new public API: `ChatService.lifecycle`.
            // Hosts whose tab-swap or overlay flow Compose can't auto-detect
            // call these on their own visibility events.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                // Pass the configured single-room JID explicitly so the call
                // works regardless of whether the user has opened the CHAT
                // tab — sample simulates a host that keeps track of its
                // listener room and doesn't rely on `RoomStore.currentRoom`.
                val resolvedJid = session.resolvedSingleRoomJid()
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        com.ethora.chat.core.ChatService.lifecycle.onChatPaused(resolvedJid)
                        LogStore.info("Playground", "ChatService.lifecycle.onChatPaused(jid=$resolvedJid)", category = "sample-ui")
                    }
                ) { Text("onChatPaused") }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        com.ethora.chat.core.ChatService.lifecycle.onChatResumed(resolvedJid)
                        LogStore.info("Playground", "ChatService.lifecycle.onChatResumed(jid=$resolvedJid)", category = "sample-ui")
                    }
                ) { Text("onChatResumed") }
            }
            Text(
                text = "Chat ready: ${if (session.isConnected) "Yes" else "No"}",
                modifier = Modifier.padding(top = 8.dp)
            )
            session.lastError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupFields(session: PlaygroundSessionState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SimpleField("Base URL", session.baseUrl) { session.baseUrl = it }
        SimpleField("App token", session.appToken) { session.appToken = it }
        SimpleField("App ID", session.appId) { session.appId = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auth mode:")
            Spacer(Modifier.padding(horizontal = 8.dp))
            androidx.compose.material3.FilterChip(
                selected = session.authMode == AuthMode.EMAIL_PASSWORD,
                onClick = { session.authMode = AuthMode.EMAIL_PASSWORD },
                label = { Text("Email") }
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            androidx.compose.material3.FilterChip(
                selected = session.authMode == AuthMode.JWT_CUSTOM,
                onClick = { session.authMode = AuthMode.JWT_CUSTOM },
                label = { Text("JWT") }
            )
        }
        if (session.authMode == AuthMode.JWT_CUSTOM) {
            SimpleField("JWT token", session.jwtToken) { session.jwtToken = it }
        } else {
            SimpleField("Email", session.email) { session.email = it }
            SimpleField("Password", session.password) { session.password = it }
        }
        SimpleField("XMPP WS URL", session.xmppWebSocketUrl) { session.xmppWebSocketUrl = it }
        SimpleField("XMPP Host", session.xmppHost) { session.xmppHost = it }
        SimpleField("XMPP Conference", session.xmppConference) { session.xmppConference = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Single chat mode")
            Spacer(Modifier.padding(horizontal = 8.dp))
            androidx.compose.material3.Switch(
                checked = session.useSingleChatMode,
                onCheckedChange = { session.useSingleChatMode = it }
            )
        }
        if (session.useSingleChatMode) {
            SimpleField("Room JID", session.singleRoomJid) { session.singleRoomJid = it }
        }
    }
}

@Composable
private fun UISettingsFields(session: PlaygroundSessionState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SimpleField("Primary color (#RRGGBB)", session.primaryColorHex) { session.primaryColorHex = it }
        SimpleField("Secondary color (#RRGGBB)", session.secondaryColorHex) { session.secondaryColorHex = it }
        SimpleField("Incoming message bg", session.incomingMessageColorHex) { session.incomingMessageColorHex = it }
        SimpleField("Outgoing message bg", session.outgoingMessageColorHex) { session.outgoingMessageColorHex = it }
        SimpleField("Incoming message text", session.incomingMessageTextColorHex) { session.incomingMessageTextColorHex = it }
        SimpleField("Outgoing message text", session.outgoingMessageTextColorHex) { session.outgoingMessageTextColorHex = it }
        SimpleField("Header color (optional)", session.headerColorHex) { session.headerColorHex = it }
        SimpleField("Input bar color (optional)", session.inputBarColorHex) { session.inputBarColorHex = it }
        SimpleField("Input text color (optional)", session.inputTextColorHex) { session.inputTextColorHex = it }
        SimpleField("Chat background (optional)", session.chatBackgroundColorHex) { session.chatBackgroundColorHex = it }
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text(if (expanded) "Hide" else "Show")
        }
    }
}

@Composable
private fun SimpleField(label: String, value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ChatTab(session: PlaygroundSessionState) {
    if (!session.isConnected) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Not connected")
            Text("Use SETUP tab to connect first.")
        }
        return
    }
    val config = session.toChatConfig()
    Chat(
        config = config,
        roomJID = session.resolvedSingleRoomJid().takeIf { session.useSingleChatMode },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun LogsTab() {
    LogsView(modifier = Modifier.fillMaxSize())
}

@Composable
private fun TestingTab(session: PlaygroundSessionState) {
    if (!session.isConnected) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Not connected")
            Text("Use SETUP tab to connect first.")
        }
        return
    }

    var selectedSubTab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSubTab) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Unread messages") }
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedSubTab) {
                0 -> UnreadMessagesPane(session = session)
            }
        }
    }
}

@Composable
private fun UnreadMessagesPane(session: PlaygroundSessionState) {
    val rooms by RoomStore.rooms.collectAsState()
    val messagesByRoom by MessageStore.messages.collectAsState()
    val currentUser by UserStore.currentUser.collectAsState()
    val meId = currentUser?.id

    if (session.useSingleChatMode) {
        val targetJid = session.resolvedSingleRoomJid()
        val room = rooms.firstOrNull { it.jid == targetJid }
        if (room == null) {
            EmptyState("Room not found: $targetJid")
        } else {
            UnreadMessagesList(
                room = room,
                messages = unreadMessagesFor(room, messagesByRoom[room.jid].orEmpty(), meId),
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (rooms.isEmpty()) {
        EmptyState("No rooms loaded yet.")
        return
    }

    var selectedJid by remember(rooms.firstOrNull()?.jid) {
        mutableStateOf(
            rooms.firstOrNull { it.unreadMessages > 0 }?.jid ?: rooms.first().jid
        )
    }
    val selectedRoom = rooms.firstOrNull { it.jid == selectedJid } ?: rooms.first()
    val unread = unreadMessagesFor(selectedRoom, messagesByRoom[selectedRoom.jid].orEmpty(), meId)

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .width(140.dp)
                .fillMaxHeight()
        ) {
            items(rooms, key = { it.jid }) { room ->
                ChatListRow(
                    room = room,
                    selected = room.jid == selectedJid,
                    onClick = { selectedJid = room.jid }
                )
                HorizontalDivider()
            }
        }
        VerticalDivider()
        UnreadMessagesList(
            room = selectedRoom,
            messages = unread,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ChatListRow(room: Room, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = room.name.ifBlank { room.title.ifBlank { room.jid.substringBefore('@') } },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            if (room.unreadMessages > 0) {
                Badge { Text(if (room.unreadCapped) "${room.unreadMessages}+" else room.unreadMessages.toString()) }
            }
        }
    }
}

@Composable
private fun UnreadMessagesList(room: Room, messages: List<Message>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MarkEmailUnread, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = room.name.ifBlank { room.title.ifBlank { room.jid } },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${messages.size} unread",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider()
        if (messages.isEmpty()) {
            EmptyState("No unread messages in this chat.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages, key = { it.id }) { msg ->
                    UnreadMessageRow(msg)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun UnreadMessageRow(msg: Message) {
    val sender = listOfNotNull(msg.user.firstName, msg.user.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { msg.user.email ?: msg.user.xmppUsername ?: msg.user.id }
    val ts = msg.timestamp ?: msg.date.time
    val time = remember(ts) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))
    }
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = sender,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.padding(top = 2.dp))
        Text(
            text = msg.body,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun unreadMessagesFor(
    room: Room,
    messages: List<Message>,
    currentUserId: String?
): List<Message> {
    val lastViewed = room.lastViewedTimestamp ?: 0L
    val baseline = room.unreadBaselineTimestamp ?: 0L
    val effective = if (lastViewed > 0L) lastViewed else baseline
    if (effective <= 0L) return emptyList()
    return messages.asSequence()
        .filter { m ->
            if (m.id == "delimiter-new") return@filter false
            if (m.pending == true) return@filter false
            if (m.sendFailed == true) return@filter false
            if (m.isDeleted == true) return@filter false
            if (m.isSystemMessage == "true") return@filter false
            if (currentUserId != null && m.user.id == currentUserId) return@filter false
            val ts = m.timestamp ?: m.date.time
            ts > 0L && ts > effective
        }
        .sortedBy { it.timestamp ?: it.date.time }
        .toList()
}

internal enum class AuthMode { EMAIL_PASSWORD, JWT_CUSTOM }

internal class PlaygroundSessionState {
    // Default every env-injected field to its BuildConfig value so that
    // whatever @ethora/setup wrote into .env (ETHORA_APP_ID, ETHORA_API_BASE_URL,
    // ETHORA_APP_TOKEN, ETHORA_USER_EMAIL/PASSWORD/JWT, ETHORA_XMPP_*) shows up
    // pre-filled in the Setup tab on first launch. If .env is absent the
    // defaults fall through to empty strings via envOrDefault() in
    // build.gradle.kts.
    //
    // authMode defaults to JWT if the env has provisioned a user JWT,
    // otherwise email/password — this lets 'setup + run' produce an
    // immediately-connectable session when a test user was created.
    var authMode by mutableStateOf(
        if (BuildConfig.ETHORA_USER_JWT.isNotBlank()) AuthMode.JWT_CUSTOM else AuthMode.EMAIL_PASSWORD
    )
    var baseUrl by mutableStateOf(BuildConfig.ETHORA_API_BASE_URL)
    var appToken by mutableStateOf(BuildConfig.ETHORA_APP_TOKEN)
    var appId by mutableStateOf(BuildConfig.ETHORA_APP_ID)
    var jwtToken by mutableStateOf(BuildConfig.ETHORA_USER_JWT)
    var email by mutableStateOf(BuildConfig.ETHORA_USER_EMAIL)
    var password by mutableStateOf(BuildConfig.ETHORA_USER_PASSWORD)
    var xmppWebSocketUrl by mutableStateOf(BuildConfig.ETHORA_XMPP_SERVER_URL)
    var xmppHost by mutableStateOf(BuildConfig.ETHORA_XMPP_HOST)
    var xmppConference by mutableStateOf(BuildConfig.ETHORA_XMPP_CONFERENCE)
    var useSingleChatMode by mutableStateOf(false)
    var singleRoomJid by mutableStateOf(BuildConfig.ETHORA_ROOM_JID)
    var primaryColorHex by mutableStateOf("#5E3FDE")
    var secondaryColorHex by mutableStateOf("#E1E4FE")
    var incomingMessageColorHex by mutableStateOf("#F2F4F8")
    var outgoingMessageColorHex by mutableStateOf("#5E3FDE")
    var incomingMessageTextColorHex by mutableStateOf("#111827")
    var outgoingMessageTextColorHex by mutableStateOf("#FFFFFF")
    var headerColorHex by mutableStateOf("")
    var inputBarColorHex by mutableStateOf("")
    var inputTextColorHex by mutableStateOf("")
    var chatBackgroundColorHex by mutableStateOf("")
    var isConnected by mutableStateOf(false)
    var isBusy by mutableStateOf(false)
    var lastError by mutableStateOf<String?>(null)

    fun toChatConfig(): ChatConfig {
        val resolvedRoom = resolvedSingleRoomJid()
        val disableRooms = useSingleChatMode
        val normalizedConference = normalizedConferenceDomain()
        val resolvedBaseUrl = baseUrl.ifBlank { BuildConfig.ETHORA_API_BASE_URL }
        val resolvedXmppServerUrl = xmppWebSocketUrl.ifBlank { BuildConfig.ETHORA_XMPP_SERVER_URL }
        val resolvedXmppHost = xmppHost.ifBlank { BuildConfig.ETHORA_XMPP_HOST }
        val resolvedConference = normalizedConference.ifBlank { BuildConfig.ETHORA_XMPP_CONFERENCE }
        val dnsOverrides = effectiveDnsFallbackOverrides(
            baseUrl = resolvedBaseUrl,
            xmppServerUrl = resolvedXmppServerUrl,
            xmppHost = resolvedXmppHost,
            conferenceHost = resolvedConference
        )
        return ChatConfig(
            appId = appId.ifBlank { null },
            baseUrl = baseUrl.ifBlank { null },
            customAppToken = appToken.ifBlank { null },
            defaultLogin = false,
            disableRooms = disableRooms,
            forceSetRoom = disableRooms,
            setRoomJidInPath = disableRooms,
            chatHeaderSettings = ChatHeaderSettingsConfig(),
            colors = com.ethora.chat.core.config.ChatColors(
                primary = normalizedHex(primaryColorHex, "#5E3FDE"),
                secondary = normalizedHex(secondaryColorHex, "#E1E4FE"),
                headerColor = headerColorHex.trim().takeIf { it.isNotEmpty() }?.let { normalizedHex(it, it) },
                inputBarColor = inputBarColorHex.trim().takeIf { it.isNotEmpty() }?.let { normalizedHex(it, it) },
                inputTextColor = inputTextColorHex.trim().takeIf { it.isNotEmpty() }?.let { normalizedHex(it, it) }
            ),
            bubleMessage = com.ethora.chat.core.config.MessageBubbleStyle(
                backgroundMessage = normalizedHex(incomingMessageColorHex, "#F2F4F8"),
                backgroundMessageUser = normalizedHex(outgoingMessageColorHex, "#5E3FDE"),
                color = normalizedHex(incomingMessageTextColorHex, "#111827"),
                colorUser = normalizedHex(outgoingMessageTextColorHex, "#FFFFFF"),
                borderRadius = 16f
            ),
            backgroundChat = chatBackgroundColorHex.trim().takeIf { it.isNotEmpty() }?.let {
                com.ethora.chat.core.config.BackgroundChatConfig(color = normalizedHex(it, "#FFFFFF"))
            },
            xmppSettings = XMPPSettings(
                xmppServerUrl = resolvedXmppServerUrl,
                host = resolvedXmppHost,
                conference = resolvedConference
            ),
            dnsFallbackOverrides = dnsOverrides.takeIf { it.isNotEmpty() },
            jwtLogin = jwtToken.takeIf { it.isNotBlank() }?.let { JWTLoginConfig(token = it, enabled = true) },
            // Kick off web-parity initBeforeLoad (xmppProvider.tsx L216-332):
            // JWT /users/client login → /chats/my → XMPP connect →
            // chatjson private store sync → 20-msg history preload per room.
            // Runs whenever the sample has a JWT configured, which is the
            // whole point of the playground's "JWT" auth mode.
            initBeforeLoad = jwtToken.isNotBlank()
        ).copy(
            chatHeaderSettings = if (resolvedRoom != null) {
                ChatHeaderSettingsConfig(roomTitleOverrides = mapOf(resolvedRoom to "Playground Room"))
            } else ChatHeaderSettingsConfig()
        )
    }

    // SDK uses these overrides as a fallback when okhttp3.Dns.SYSTEM throws
    // UnknownHostException — see DnsFallback.kt in the SDK. Both ApiClient
    // (HTTP) and XMPPWebSocketConnection install the same fallback Dns, so
    // these entries cover both transports.
    //
    // Source resolution (later wins, dedup by host):
    //   1. ETHORA_DNS_FALLBACK_OVERRIDES env var / .env (comma- or
    //      semicolon- or newline-separated `host=ip` pairs)
    //   2. Hard-coded emergency for the *.messenger-dev2.vitall.com dev
    //      cluster — verified via `dig`, all hosts on this domain currently
    //      live on 15.156.203.25. The Android emulator's DNS is unreliable
    //      on some networks, and physical phones resolve fine, so without
    //      this entry the dev cluster is unreachable from emulator-only
    //      developer machines while it works for everyone else.
    private fun effectiveDnsFallbackOverrides(
        baseUrl: String,
        xmppServerUrl: String,
        xmppHost: String,
        conferenceHost: String
    ): Map<String, String> {
        val overrides = envDnsFallbackOverrides().toMutableMap()

        val emergencyIp = "15.156.203.25"
        val hosts = linkedSetOf<String>()
        extractHost(baseUrl)?.let { hosts += it }
        extractHost(xmppServerUrl)?.let { hosts += it }
        if (xmppHost.isNotBlank()) hosts += xmppHost.trim().lowercase()
        if (conferenceHost.isNotBlank()) hosts += conferenceHost.trim().lowercase()
        hosts.filter { it.endsWith(".messenger-dev2.vitall.com") || it == "messenger-dev2.vitall.com" }
            .forEach { host -> overrides.putIfAbsent(host, emergencyIp) }

        return overrides
    }

    private fun envDnsFallbackOverrides(): Map<String, String> {
        val raw = BuildConfig.ETHORA_DNS_FALLBACK_OVERRIDES.trim()
        if (raw.isEmpty()) return emptyMap()
        return raw.split(",", ";", "\n")
            .mapNotNull { entry ->
                val line = entry.trim()
                if (line.isEmpty()) return@mapNotNull null
                val sep = line.indexOf('=').takeIf { it > 0 }
                    ?: line.indexOf(':').takeIf { it > 0 }
                    ?: return@mapNotNull null
                val host = line.substring(0, sep).trim().lowercase()
                val ip = line.substring(sep + 1).trim()
                if (host.isEmpty() || ip.isEmpty()) null else host to ip
            }
            .toMap()
    }

    private fun extractHost(url: String): String? {
        return runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull()
    }

    fun resolvedSingleRoomJid(): String? {
        val raw = singleRoomJid.trim()
        if (raw.isEmpty()) return null
        if (raw.contains("@")) return raw
        val conference = normalizedConferenceDomain()
        if (conference.isBlank()) return raw
        return "$raw@$conference"
    }

    fun toJson(): String {
        val obj = org.json.JSONObject()
            .put("state", org.json.JSONObject()
                .put("isConnected", isConnected))
            .put("auth", org.json.JSONObject()
                .put("mode", if (authMode == AuthMode.JWT_CUSTOM) "jwt" else "email")
                .put("email", email)
                .put("password", password)
                .put("token", jwtToken))
            .put("api", org.json.JSONObject()
                .put("baseUrl", baseUrl)
                .put("appToken", appToken)
                .put("appId", appId))
            .put("xmpp", org.json.JSONObject()
                .put("webSocketUrl", xmppWebSocketUrl)
                .put("host", xmppHost)
                .put("conference", xmppConference))
            .put("chat", org.json.JSONObject()
                .put("singleRoomMode", useSingleChatMode)
                .put("roomJid", singleRoomJid))
            .put("ui", org.json.JSONObject()
                .put("primaryColorHex", primaryColorHex)
                .put("secondaryColorHex", secondaryColorHex)
                .put("incomingMessageColorHex", incomingMessageColorHex)
                .put("outgoingMessageColorHex", outgoingMessageColorHex)
                .put("incomingMessageTextColorHex", incomingMessageTextColorHex)
                .put("outgoingMessageTextColorHex", outgoingMessageTextColorHex)
                .put("headerColorHex", headerColorHex)
                .put("inputBarColorHex", inputBarColorHex)
                .put("inputTextColorHex", inputTextColorHex)
                .put("chatBackgroundColorHex", chatBackgroundColorHex))
        return obj.toString(2)
    }

    fun applyJson(raw: String) {
        val root = org.json.JSONObject(raw)
        root.optJSONObject("state")?.let { state ->
            isConnected = state.optBoolean("isConnected", isConnected)
        }
        root.optJSONObject("auth")?.let { auth ->
            val mode = auth.optString("mode", "")
            authMode = if (mode.contains("jwt", ignoreCase = true)) AuthMode.JWT_CUSTOM else AuthMode.EMAIL_PASSWORD
            email = auth.optString("email", email)
            password = auth.optString("password", password)
            jwtToken = auth.optString("token", jwtToken)
        }
        root.optJSONObject("api")?.let { api ->
            baseUrl = api.optString("baseUrl", baseUrl)
            appToken = api.optString("appToken", appToken)
            appId = api.optString("appId", appId)
        }
        root.optJSONObject("xmpp")?.let { xmpp ->
            xmppWebSocketUrl = xmpp.optString("webSocketUrl", xmppWebSocketUrl)
            xmppHost = xmpp.optString("host", xmppHost)
            xmppConference = normalizeConferenceDomain(
                xmpp.optString("conference", xmppConference)
            )
        }
        root.optJSONObject("chat")?.let { chat ->
            useSingleChatMode = chat.optBoolean("singleRoomMode", useSingleChatMode)
            singleRoomJid = chat.optString("roomJid", singleRoomJid)
        }
        root.optJSONObject("ui")?.let { ui ->
            primaryColorHex = ui.optString("primaryColorHex", primaryColorHex)
            secondaryColorHex = ui.optString("secondaryColorHex", secondaryColorHex)
            incomingMessageColorHex = ui.optString("incomingMessageColorHex", incomingMessageColorHex)
            outgoingMessageColorHex = ui.optString("outgoingMessageColorHex", outgoingMessageColorHex)
            incomingMessageTextColorHex = ui.optString("incomingMessageTextColorHex", incomingMessageTextColorHex)
            outgoingMessageTextColorHex = ui.optString("outgoingMessageTextColorHex", outgoingMessageTextColorHex)
            headerColorHex = ui.optString("headerColorHex", headerColorHex)
            inputBarColorHex = ui.optString("inputBarColorHex", inputBarColorHex)
            inputTextColorHex = ui.optString("inputTextColorHex", inputTextColorHex)
            chatBackgroundColorHex = ui.optString("chatBackgroundColorHex", chatBackgroundColorHex)
        }
    }

    private fun normalizedHex(raw: String, fallback: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return fallback
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    fun normalizedConferenceDomain(): String {
        return normalizeConferenceDomain(xmppConference)
    }

    private fun normalizeConferenceDomain(raw: String): String {
        var value = raw.trim().lowercase()
        if (value.isEmpty()) return value
        value = value.removePrefix("wss://").removePrefix("ws://")
        value = value.removePrefix("https://").removePrefix("http://")
        value = value.substringBefore("/")
        if (value.startsWith("conferenceconference.")) {
            value = value.replaceFirst("conferenceconference.", "conference.")
        }
        return value
    }

    companion object {
        private const val PREFS_NAME = "sdk_playground"
        private const val KEY_JSON = "setup_json"
        private const val KEY_SCHEMA_VERSION = "schema_version"

        /**
         * Bump whenever the set or semantics of PlaygroundSessionState
         * defaults changes — e.g. a new BuildConfig-backed field is added,
         * an existing default changes, or a field is renamed.
         *
         * On load(), if the persisted schema doesn't match the current one,
         * the saved JSON is discarded and the mutableStateOf defaults
         * (which now read from BuildConfig.*) take effect. This means a
         * new build produced by @ethora/setup isn't silently overwritten
         * by stale JSON from a previous install, while a developer's own
         * edits still survive app restarts within the same schema.
         *
         * History:
         *   1 — initial (pre-BuildConfig wiring)
         *   2 — added ETHORA_APP_TOKEN / ETHORA_USER_EMAIL /
         *       ETHORA_USER_PASSWORD; every default now reads from
         *       BuildConfig.*
         */
        private const val CURRENT_SCHEMA_VERSION = 2

        fun load(context: Context): PlaygroundSessionState {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val state = PlaygroundSessionState()
            val savedSchema = prefs.getInt(KEY_SCHEMA_VERSION, 0)
            if (savedSchema == CURRENT_SCHEMA_VERSION) {
                val savedJson = prefs.getString(KEY_JSON, null)
                if (!savedJson.isNullOrBlank()) {
                    kotlin.runCatching { state.applyJson(savedJson) }
                }
            }
            // Stale-schema path intentionally falls through to BuildConfig
            // defaults without touching prefs — the first save() will
            // rewrite KEY_JSON + KEY_SCHEMA_VERSION together.
            state.xmppConference = state.normalizedConferenceDomain()
            return state
        }

        fun save(context: Context, state: PlaygroundSessionState) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                .putString(KEY_JSON, state.toJson())
                .apply()
        }

        fun prettyJson(raw: String): String = org.json.JSONObject(raw).toString(2)
    }
}
