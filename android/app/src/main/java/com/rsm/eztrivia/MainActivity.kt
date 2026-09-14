package com.rsm.eztrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rsm.eztrivia.ads.AdConsentManager
import com.rsm.eztrivia.billing.BillingManager
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.playgames.PlayGamesManager
import com.rsm.eztrivia.ui.EZTriviaApp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var playGamesManager: PlayGamesManager
        private set
    lateinit var adConsentManager: AdConsentManager
        private set
    lateinit var billingManager: BillingManager
        private set

    private lateinit var playerStateStore: PlayerStateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playGamesManager = PlayGamesManager(this)
        adConsentManager = AdConsentManager(this)
        billingManager = BillingManager(this)
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

        billingManager.start()
        adConsentManager.configure()
    }

    override fun onResume() {
        super.onResume()
        if (::playGamesManager.isInitialized) {
            playGamesManager.refreshAuthentication()
        }
        if (::billingManager.isInitialized) {
            billingManager.refresh()
        }
    }

    override fun onDestroy() {
        if (::billingManager.isInitialized) {
            billingManager.close()
        }
        super.onDestroy()
    }
}
