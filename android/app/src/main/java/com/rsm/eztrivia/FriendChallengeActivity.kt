package com.rsm.eztrivia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.model.FriendChallengeLink
import com.rsm.eztrivia.ui.FriendChallengeApp
import kotlinx.coroutines.launch

class FriendChallengeActivity : ComponentActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FriendChallengeApp(
                    incomingUrl = incomingUrl,
                    onIncomingUrlConsumed = { incomingUrl = null },
                    onBackToPlay = ::returnToPlay,
                )
            }
        }
        routeIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeIncomingIntent(intent)
    }

    /**
     * Resolve the disk-backed attempt record before exposing the URL to Compose.
     * This closes the cold-start window where StateFlow still carries its empty
     * initial value even though the same challenge was completed previously.
     */
    private fun routeIncomingIntent(intent: Intent?) {
        val rawUrl = intent?.dataString ?: return
        lifecycleScope.launch {
            FriendChallengeLink.codeFrom(rawUrl)?.let { code ->
                PlayerStateStore(applicationContext).persistedFriendChallengeResult(code)
            }
            incomingUrl = rawUrl
        }
    }

    private fun returnToPlay() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}
