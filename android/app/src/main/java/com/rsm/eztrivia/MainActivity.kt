package com.rsm.eztrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rsm.eztrivia.ads.AdConsentManager
import com.rsm.eztrivia.billing.RemoveAdsBillingManager
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.playgames.PlayGamesManager
import com.rsm.eztrivia.playgames.PlayGamesSavedStateManager
import com.rsm.eztrivia.ui.EZTriviaApp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var playGamesManager: PlayGamesManager
        private set

    lateinit var savedGamesManager: PlayGamesSavedStateManager
        private set

    lateinit var removeAdsBillingManager: RemoveAdsBillingManager
        private set

    lateinit var adConsentManager: AdConsentManager
        private set

    private lateinit var playerStateStore: PlayerStateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerStateStore = PlayerStateStore(applicationContext)
        playGamesManager = PlayGamesManager(this)
        savedGamesManager = PlayGamesSavedStateManager(this, playerStateStore, lifecycleScope)
        removeAdsBillingManager = RemoveAdsBillingManager(this)
        adConsentManager = AdConsentManager(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(playerStateStore.state, playGamesManager.connection) { state, connection ->
                    state to connection
                }.collect { (state, connection) ->
                    if (connection.isAuthenticated) {
                        playGamesManager.sync(state)
                        // Saved Games requests are debounced/coalesced inside
                        // the manager, so question-by-question PlayerState
                        // changes never block gameplay or hammer the service.
                        savedGamesManager.requestSync()
                    }
                }
            }
        }

        // Paid players should not be asked for advertising consent when no ad
        // request will be made. If Play later clears a cached entitlement after
        // a refund/revocation, the consent flow starts automatically.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                removeAdsBillingManager.state.collect { purchaseState ->
                    if (!purchaseState.hasRemovedAds) {
                        adConsentManager.configure()
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
            // This is also the initial connection. On later resumes it rechecks
            // ownership so purchases, refunds, and revocations stay current.
            removeAdsBillingManager.refresh()
        }
    }

    override fun onDestroy() {
        if (::savedGamesManager.isInitialized) {
            savedGamesManager.close()
        }
        if (::removeAdsBillingManager.isInitialized) {
            removeAdsBillingManager.close()
        }
        super.onDestroy()
    }
}
