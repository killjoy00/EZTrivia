package com.rsm.eztrivia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.data.QuestionCatalog
import com.rsm.eztrivia.model.TriviaQuestion
import com.rsm.eztrivia.ui.DailyChallengeScreen

class DailyChallengeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DailyChallengeEntry(onBackToPlay = ::returnToPlay)
            }
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

@Composable
private fun DailyChallengeEntry(onBackToPlay: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { PlayerStateStore(context.applicationContext) }
    val playerState by store.state.collectAsState()
    val catalogResult by produceState<Result<List<TriviaQuestion>>?>(initialValue = null) {
        value = runCatching { QuestionCatalog.load(context) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val result = catalogResult) {
            null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> result.fold(
                onSuccess = { catalog ->
                    DailyChallengeScreen(
                        catalog = catalog,
                        playerState = playerState,
                        store = store,
                        onBack = onBackToPlay,
                    )
                },
                onFailure = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Text("Daily Challenge unavailable")
                    }
                },
            )
        }
    }
}
