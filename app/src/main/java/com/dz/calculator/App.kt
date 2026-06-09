package com.dz.calculator

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.dz.calculator.history.HistoryService
import com.dz.calculator.session.SessionService
import com.dz.calculator.settings.Preferences

class App : Application() {

    lateinit var historyService: HistoryService
    lateinit var sessionService: SessionService

    override fun onCreate() {
        super.onCreate()
        historyService = HistoryService(this)
        sessionService = SessionService(this)

        val preferences = Preferences(this)
        when (preferences.getTheme()) {
            -1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}