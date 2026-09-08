package com.rsm.eztrivia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rsm.eztrivia.ui.FriendChallengeApp

class FriendChallengeActivity : ComponentActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUrl = intent?.dataString
        setContent {
            MaterialTheme {
                FriendChallengeApp(
                    incomingUrl = incomingUrl,
                    onIncomingUrlConsumed = { incomingUrl = null },
                    onBackToPlay = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl = intent.dataString
    }
}
