package com.dz.calculator

import android.app.Application
import com.dz.calculator.history.HistoryService

class App: Application() {
    val historyService: HistoryService by lazy {
        HistoryService(this)
    }
}