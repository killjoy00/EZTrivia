package com.rsm.eztrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rsm.eztrivia.billing.RemoveAdsBillingManager
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.playgames.PlayGamesManager
import com.rsm.eztrivia.ui.EZTriviaApp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var playGamesManager: PlayGamesManager
        private set

    lateinit var removeAdsBillingManager: RemoveAdsBillingManager
        private set

    private lateinit var playerStateStore: PlayerStateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playGamesManager = PlayGamesManager(this)
        removeAdsBillingManager = RemoveAdsBillingManager(this).also { it.start() }
        playerStateStore = PlayerStateStore(applicationContext)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(playerStateStore.state, playGamesManager.connection) { state, connection ->
                    state to connection
                }.collect { (state, connection) ->
                    if (connection.isAuthenticated) {
                        playGamesManager.sync(state)
                    }
                }
            }
        }

        setContent {
            MaterialTheme {
                EZTriviaApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::playGamesManager.isInitialized) {
            playGamesManager.refreshAuthentication()
        }
        if (::removeAdsBillingManager.isInitialized) {
            removeAdsBillingManager.refresh()
        }
    }

    override fun onDestroy() {
        if (::removeAdsBillingManager.isInitialized) {
            removeAdsBillingManager.close()
        }
        super.onDestroy()
    }
}
