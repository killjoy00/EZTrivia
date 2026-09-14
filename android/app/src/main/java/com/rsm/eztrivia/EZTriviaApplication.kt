package com.rsm.eztrivia

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk

class EZTriviaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayGamesSdk.initialize(this)
    }
}
