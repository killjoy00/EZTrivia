package com.rsm.eztrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.rsm.eztrivia.playgames.PlayGamesManager
import com.rsm.eztrivia.ui.EZTriviaApp

class MainActivity : ComponentActivity() {
    private lateinit var playGamesManager: PlayGamesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playGamesManager = PlayGamesManager(this)
        setContent {
            MaterialTheme {
                EZTriviaApp(playGamesManager = playGamesManager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::playGamesManager.isInitialized) {
            playGamesManager.refreshAuthentication()
        }
    }
}
