package com.example.privacyshield

import android.app.Application

class ShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DisplayShield.init(this)
    }
}
