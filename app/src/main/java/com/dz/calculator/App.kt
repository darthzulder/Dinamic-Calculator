package com.dz.calculator

import android.app.Application
import com.dz.calculator.history.HistoryService
import com.dz.calculator.session.SessionService

class App : Application() {

    lateinit var historyService: HistoryService
    lateinit var sessionService: SessionService

    override fun onCreate() {
        super.onCreate()
        historyService = HistoryService(this)
        sessionService = SessionService(this)
    }
}