package com.ethora.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ethora.chat.Chat
import com.ethora.chat.core.config.AppConfig
import com.ethora.chat.core.config.ChatConfig
import com.ethora.chat.core.config.XMPPSettings
import com.ethora.chat.core.networking.ApiClient
import com.ethora.chat.core.store.ChatStore
import com.ethora.chat.core.store.RoomStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SampleChatApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleChatApp() {
    val missingFields = remember { collectMissingConfigFields() }
    var selectedTab by remember { mutableStateOf(0) }
    val rooms by RoomStore.rooms.collectAsState()
    val totalUnread = remember(rooms) { rooms.sumOf { it.unreadMessages } }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (missingFields.isNotEmpty()) {
                SetupRequiredScreen(missingFields)
                return@Surface
            }

            val chatConfig = remember {
                ChatConfig(
                    appId = BuildConfig.ETHORA_APP_ID,
                    baseUrl = BuildConfig.ETHORA_API_BASE_URL,
                    customAppToken = BuildConfig.ETHORA_APP_TOKEN
                        .takeIf { it != "CHANGE_ME" && it.isNotBlank() },
                    xmppSettings = XMPPSettings(
                        xmppServerUrl = BuildConfig.ETHORA_XMPP_SERVER_URL,
                        host = BuildConfig.ETHORA_XMPP_HOST,
                        conference = BuildConfig.ETHORA_XMPP_CONFERENCE
                    )
                )
            }

            LaunchedEffect(chatConfig) {
                ChatStore.setConfig(chatConfig)
                ApiClient.setBaseUrl(
                    chatConfig.baseUrl ?: AppConfig.defaultBaseURL,
                    chatConfig.customAppToken
                )
            }

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                if (totalUnread > 0) {
                                    BadgedBox(badge = { Badge { Text(totalUnread.toString()) } }) {
                                        Icon(Icons.Default.Email, contentDescription = "Chat")
                                    }
                                } else {
                                    Icon(Icons.Default.Email, contentDescription = "Chat")
                                }
                            },
                            label = { Text("Chat") }
                        )
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                    if (selectedTab == 0) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ethora Sample App",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Tap Chat to start messaging",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        Chat(
                            config = chatConfig,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun collectMissingConfigFields(): List<String> {
    val fields = mutableListOf<String>()
    if (BuildConfig.ETHORA_APP_ID == "CHANGE_ME") fields += "ETHORA_APP_ID"
    if (BuildConfig.ETHORA_API_BASE_URL == "CHANGE_ME") fields += "ETHORA_API_BASE_URL"
    if (BuildConfig.ETHORA_XMPP_SERVER_URL == "CHANGE_ME") fields += "ETHORA_XMPP_SERVER_URL"
    if (BuildConfig.ETHORA_XMPP_HOST == "CHANGE_ME") fields += "ETHORA_XMPP_HOST"
    if (BuildConfig.ETHORA_XMPP_CONFERENCE == "CHANGE_ME") fields += "ETHORA_XMPP_CONFERENCE"
    return fields
}

@Composable
private fun SetupRequiredScreen(missingFields: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Setup Required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text("Run the Ethora setup tool to configure this app:")
        Text(
            text = "npx @ethora/setup",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Or update these fields manually in app/build.gradle.kts:",
            style = MaterialTheme.typography.bodyMedium
        )
        missingFields.forEach { field ->
            Text(text = "  • $field", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
