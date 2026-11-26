package com.rsm.eztrivia

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class EZTriviaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayGamesSdk.initialize(this)

        if (BuildConfig.CRASHLYTICS_CONFIGURED) {
            FirebaseApp.initializeApp(this)?.let {
                FirebaseCrashlytics.getInstance().apply {
                    // Debug/emulator work should never pollute production crash data.
                    // Release builds collect crashes and ANRs; Firebase Analytics is
                    // intentionally not linked or included in this app.
                    setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
                    setCustomKey("version_code", BuildConfig.VERSION_CODE)
                    setCustomKey("version_name", BuildConfig.VERSION_NAME)
                    setCustomKey("build_type", BuildConfig.BUILD_TYPE)
                }
            }
        }
    }
}
