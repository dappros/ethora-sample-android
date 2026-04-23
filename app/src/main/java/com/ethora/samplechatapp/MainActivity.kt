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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethora.chat.Chat
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
import androidx.core.content.ContextCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ethora.chat.core.persistence.ChatDatabase
import com.ethora.chat.core.persistence.ChatPersistenceManager
import com.ethora.chat.core.persistence.LocalStorage
import com.ethora.chat.core.persistence.MessageCache
import com.ethora.chat.core.push.PushNotificationManager
import com.ethora.chat.core.store.MessageLoader
import com.ethora.chat.core.store.MessageStore
import com.ethora.chat.core.store.ScrollPositionStore
import com.ethora.chat.core.store.UserStore
import com.ethora.chat.core.store.LogStore
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
        initChatStores()
        PushNotificationManager.initialize(this)
        logSigningCertificateSha1()
        isFirebaseAvailable = checkFirebaseInit()
        if (isFirebaseAvailable) {
            logGooglePlayServicesStatus()
            logFirebaseInstallationId()
        } else {
            Log.w(TAG, "Firebase is not configured (no google-services.json). Skip FCM init.")
        }
        requestNotificationPermission()
        if (isFirebaseAvailable) {
            scheduleFcmTokenFetchOnce()
        }
        handleNotificationIntent(intent)

        setContent {
            SampleChatApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun initChatStores() {
        val context = applicationContext
        val persistenceManager = ChatPersistenceManager(context)
        val chatDatabase = ChatDatabase.getDatabase(context)
        val messageCache = MessageCache(chatDatabase)
        RoomStore.initialize(persistenceManager)
        UserStore.initialize(persistenceManager)
        MessageStore.initialize(messageCache)
        ScrollPositionStore.initialize(context)
        MessageLoader.initialize(LocalStorage(context))
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
    val logs = remember { mutableStateListOf<LogLine>() }
    val rooms by RoomStore.rooms.collectAsState()
    val unreadTotal = remember(rooms) { rooms.sumOf { it.unreadMessages } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // First-launch build stamp so every pasted log dump identifies the
    // exact build. Format: "<short sha> @ <YY.MM.DD.HH:mm UTC> on <branch>".
    // The SDK version is the JitPack coordinate pinned in app/build.gradle.kts.
    LaunchedEffect(Unit) {
        logs.add(
            0,
            LogLine.info(
                "sample-chat-app build=${BuildConfig.SAMPLE_GIT_SHA} " +
                    "@${BuildConfig.SAMPLE_BUILD_TIME}UTC " +
                    "branch=${BuildConfig.SAMPLE_GIT_BRANCH}"
            )
        )
    }

    LaunchedEffect(rooms.size) {
        logs.add(0, LogLine.info("Rooms updated: ${rooms.size}"))
    }

    // Surface every ChatConnectionStore transition into the playground
    // Logs tab so developers can see whether the SDK actually thinks it's
    // ONLINE. Key gate: ChatInput's canSendMessage check in the SDK only
    // enables the Send button when this status is ONLINE. If your Send
    // button is grey even after 'Chat ready: Yes', the transitions logged
    // here will reveal whether the store is stuck at CONNECTING /
    // DEGRADED / OFFLINE, or it actually is ONLINE and something else
    // (e.g. XMPP MUC join) is blocking sends.
    LaunchedEffect(Unit) {
        com.ethora.chat.core.store.ConnectionStore.state.collect { state ->
            val reason = state.reason?.let { " — $it" } ?: ""
            val recovering = if (state.isRecovering) " (recovering)" else ""
            logs.add(0, LogLine.info("[ConnectionStore] ${state.status}${reason}${recovering}"))
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
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
                                if (unreadTotal > 0) {
                                    BadgedBox(badge = { Badge { Text(unreadTotal.toString()) } }) {
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
                    // Keep-all-tabs-alive dispatch.
                    //
                    // Previous `when (selectedTab) { ... }` pattern unmounted
                    // the inactive tab's subtree on every tab change. For
                    // the Chat tab that meant EthoraChat's `remember {
                    // XMPPClientRegistry.getOrCreate(...) }` slot was
                    // disposed, which tore down delegate wiring. On
                    // re-entry a new remember slot asked the registry
                    // again, and we saw multiple clients race. Now the
                    // XMPPClientRegistry cache returns the same instance
                    // even across dispose, but we additionally keep each
                    // tab's composition alive via alpha-gated stacking so
                    // per-tab UI state (scroll position, expanded cards,
                    // form field focus) also survives tab switches.
                    TabSlot(visible = selectedTab == 0) {
                        SetupTab(
                            context = context,
                            session = session,
                            logs = logs,
                            onConnect = {
                                scope.launchConnection(context, session, logs)
                            },
                            onDisconnect = {
                                session.isConnected = false
                                session.lastError = null
                                UserStore.clear()
                                logs.add(0, LogLine.warning("Disconnected. Session cleared."))
                                PlaygroundSessionState.save(context, session)
                            }
                        )
                    }
                    TabSlot(visible = selectedTab == 1) {
                        ChatTab(session = session)
                    }
                    TabSlot(visible = selectedTab == 2) {
                        LogsTab(logs)
                    }
                }
            }
        }
    }
}

private fun CoroutineScope.launchConnection(
    context: Context,
    session: PlaygroundSessionState,
    logs: MutableList<LogLine>
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
            logs.add(0, LogLine.warning("Fixed XMPP Conference domain: ${session.xmppConference} -> $fixed"))
            LogStore.warning("Playground", "Normalized XMPP conference domain to $fixed")
            session.xmppConference = fixed
        }
        ChatStore.setConfig(config)
        ApiClient.setBaseUrl(config.baseUrl ?: AppConfig.defaultBaseURL, config.customAppToken)
        when (session.authMode) {
            AuthMode.JWT_CUSTOM -> {
                if (session.jwtToken.isBlank()) error("JWT token is required.")
                logs.add(0, LogLine.info("Auth: login via JWT"))
                val response = com.ethora.chat.core.networking.AuthAPIHelper.loginViaJWT(
                    token = session.jwtToken,
                    baseUrl = session.baseUrl
                ) ?: error("JWT login failed.")
                UserStore.setUser(response)
                logs.add(0, LogLine.success("Auth success (JWT): ${response.user.email ?: response.user.username ?: response.user._id}"))
                LogStore.success("Playground", "HTTP login success (JWT)")
            }
            AuthMode.EMAIL_PASSWORD -> {
                if (session.email.isBlank() || session.password.isBlank()) {
                    error("Email and password are required.")
                }
                logs.add(0, LogLine.info("Auth: login with email"))
                val response = com.ethora.chat.core.networking.AuthAPIHelper.loginWithEmail(
                    email = session.email,
                    password = session.password,
                    baseUrl = session.baseUrl
                )
                UserStore.setUser(response)
                logs.add(0, LogLine.success("Auth success (email): ${response.user.email ?: response.user._id}"))
                LogStore.success("Playground", "HTTP login success (email)")
            }
        }
        val xmppUser = UserStore.currentUser.value?.xmppUsername
        val xmppPass = UserStore.currentUser.value?.xmppPassword
        if (xmppUser.isNullOrBlank() || xmppPass.isNullOrBlank()) {
            LogStore.warning("Playground", "XMPP credentials are empty in login response. Messages will not load.")
            logs.add(0, LogLine.warning("XMPP credentials missing in response"))
        } else {
            LogStore.info("Playground", "XMPP credentials present for user=$xmppUser")
        }
        session.isConnected = true
        PlaygroundSessionState.save(context, session)
    } catch (e: Exception) {
        session.lastError = e.message ?: "Connection failed"
        logs.add(0, LogLine.error("Connect failed: ${session.lastError}"))
        LogStore.error("Playground", "Connect failed: ${session.lastError}")
    } finally {
        session.isBusy = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupTab(
    context: Context,
    session: PlaygroundSessionState,
    logs: MutableList<LogLine>,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var jsonMode by remember { mutableStateOf(false) }
    var jsonValue by remember { mutableStateOf(session.toJson()) }
    var authExpanded by remember { mutableStateOf(true) }
    var uiExpanded by remember { mutableStateOf(false) }
    // Outer container handles IME inset lifting; no padding here so the
    // scroll/column children can own their own padding and the scroll
    // area can run all the way to the system bars.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
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
                // weight(1f) lets the LazyColumn absorb all remaining
                // vertical space inside the Column above the system-bar
                // padding. Previously this was fillMaxSize() inside a
                // Column with a fixed 120.dp bottom padding to leave
                // room for an absolute-positioned button bar — that bar
                // frequently clipped long form fields (notably the JWT
                // token textarea). Moving the buttons into the scroll
                // as the final item removes the overlap entirely.
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                item { ConnectBar(session, onConnect, onDisconnect) }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                            logs.add(0, LogLine.success("JSON formatted"))
                        }.onFailure { logs.add(0, LogLine.error("JSON error: ${it.message}")) }
                    }) { Text("Format JSON") }
                    androidx.compose.material3.Button(onClick = {
                        kotlin.runCatching {
                            session.applyJson(jsonValue)
                            jsonValue = session.toJson()
                            logs.add(0, LogLine.success("JSON applied"))
                            PlaygroundSessionState.save(context, session)
                        }.onFailure { logs.add(0, LogLine.error("JSON error: ${it.message}")) }
                    }) { Text("Apply JSON") }
                }
                Spacer(Modifier.padding(top = 4.dp))
                ConnectBar(session, onConnect, onDisconnect)
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/**
 * Always-composed tab container.
 *
 * When `visible == false` the content is still in composition (state
 * preserved, SDK clients live) but invisible (alpha 0) and intercepts
 * no pointer events. When `visible == true` the content renders
 * normally. This replaces the previous
 * `when (selectedTab) { 0 -> SetupTab(...); 1 -> ChatTab(...) }`
 * pattern, which disposed inactive tabs and caused the Chat tab's
 * SDK wiring to tear down on every tab switch.
 */
@Composable
private fun TabSlot(visible: Boolean, content: @Composable () -> Unit) {
    // Three TabSlots are stacked in a Box at fillMaxSize. Box hit-testing
    // picks the topmost child (by draw order, i.e. the last-composed one)
    // regardless of visual transparency. Without zIndex, whichever tab
    // happens to be composed last always caught taps — the visible tab
    // underneath was completely unresponsive.
    //
    // zIndex(1f) lifts the active tab above the inactive ones so hit
    // testing goes to the visible tab. Inactive tabs stay in composition
    // (SDK clients / scroll state preserved) but are below the visible
    // one and drawn at alpha 0, so they render nothing and receive no
    // events.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .graphicsLayer(alpha = if (visible) 1f else 0f)
    ) {
        content()
    }
}

/**
 * Connect / Disconnect row + status line. Previously rendered as an
 * absolute-positioned Column at the bottom of a Box, which overlapped
 * the scrollable form fields (the JWT textarea was the most common
 * casualty). Now inlined at the end of the scroll content in both
 * Fields and JSON modes.
 */
@Composable
private fun ConnectBar(
    session: PlaygroundSessionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
    val connectionState by com.ethora.chat.core.store.ConnectionStore.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        // Thin indicator above the SDK's chat UI. The SDK's Send button
        // is gated on ConnectionStore.state.status == ONLINE, so surfacing
        // the live status here makes it obvious when the Send button is
        // disabled because of connection state vs some other reason.
        ConnectionStatusBanner(connectionState)
        Chat(
            config = config,
            roomJID = session.resolvedSingleRoomJid().takeIf { session.useSingleChatMode },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ConnectionStatusBanner(state: com.ethora.chat.core.store.ChatConnectionState) {
    val (bg, fg, label) = when (state.status) {
        com.ethora.chat.core.store.ChatConnectionStatus.ONLINE ->
            Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "ONLINE — send enabled")
        com.ethora.chat.core.store.ChatConnectionStatus.CONNECTING ->
            Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "CONNECTING…")
        com.ethora.chat.core.store.ChatConnectionStatus.DEGRADED ->
            Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "DEGRADED")
        com.ethora.chat.core.store.ChatConnectionStatus.ERROR ->
            Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "ERROR")
        com.ethora.chat.core.store.ChatConnectionStatus.OFFLINE ->
            Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "OFFLINE — send disabled")
    }
    val reason = state.reason?.let { " · $it" } ?: ""
    androidx.compose.material3.Surface(
        color = bg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "[ConnectionStore] $label$reason",
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LogsTab(logs: MutableList<LogLine>) {
    var query by remember { mutableStateOf("") }
    val sdkLogs by LogStore.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    val merged = remember(logs.size, sdkLogs.size) {
        val local = logs.map { "${it.time} [${it.level}] ${it.message}" }
        val sdk = sdkLogs.map { "${it.timestamp} [${it.type}] [${it.tag}] ${it.message}" }
        (local + sdk)
    }
    val filtered = remember(merged.size, query) {
        if (query.isBlank()) merged
        else merged.filter { it.contains(query, ignoreCase = true) }
    }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Filter logs") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(onClick = {
                logs.clear()
                logs.add(0, LogLine.info("Logs cleared."))
                LogStore.clear()
            }) { Text("Clear") }
            androidx.compose.material3.TextButton(onClick = {
                // Copy the current filtered view to the system clipboard
                // so developers can paste logs into a bug report without
                // fiddling with long-press selection on every line.
                val payload = filtered.joinToString("\n")
                clipboard.setText(AnnotatedString(payload))
                logs.add(0, LogLine.success("Copied ${filtered.size} log line(s) to clipboard"))
            }) { Text("Copy all") }
        }
        // SelectionContainer turns all descendant Text nodes into
        // selectable text — long-press to start a selection, drag handles
        // to extend, and the standard Android copy/share sheet appears.
        SelectionContainer(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered.size) { idx ->
                    val line = filtered[idx]
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private enum class AuthMode { EMAIL_PASSWORD, JWT_CUSTOM }

private class PlaygroundSessionState {
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
    var chatBackgroundColorHex by mutableStateOf("")
    var isConnected by mutableStateOf(false)
    var isBusy by mutableStateOf(false)
    var lastError by mutableStateOf<String?>(null)

    fun toChatConfig(): ChatConfig {
        val resolvedRoom = resolvedSingleRoomJid()
        val disableRooms = useSingleChatMode
        val normalizedConference = normalizedConferenceDomain()
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
                secondary = normalizedHex(secondaryColorHex, "#E1E4FE")
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
                xmppServerUrl = xmppWebSocketUrl.ifBlank { BuildConfig.ETHORA_XMPP_SERVER_URL },
                host = xmppHost.ifBlank { BuildConfig.ETHORA_XMPP_HOST },
                conference = normalizedConference.ifBlank { BuildConfig.ETHORA_XMPP_CONFERENCE }
            ),
            jwtLogin = jwtToken.takeIf { it.isNotBlank() }?.let { JWTLoginConfig(token = it, enabled = true) }
        ).copy(
            chatHeaderSettings = if (resolvedRoom != null) {
                ChatHeaderSettingsConfig(roomTitleOverrides = mapOf(resolvedRoom to "Playground Room"))
            } else ChatHeaderSettingsConfig()
        )
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

private data class LogLine(
    val time: String,
    val level: String,
    val message: String
) {
    companion object {
        fun info(message: String) = LogLine(now(), "info", message)
        fun success(message: String) = LogLine(now(), "success", message)
        fun warning(message: String) = LogLine(now(), "warning", message)
        fun error(message: String) = LogLine(now(), "error", message)

        private fun now(): String {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            return sdf.format(java.util.Date())
        }
    }
}
