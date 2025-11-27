package com.dz.calculator.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

typealias SessionDataListListener = (sessionList: List<SessionData>) -> Unit

class SessionService(context: Context) {
    private var sessionList = mutableListOf<SessionData>()
    private val listeners = mutableSetOf<SessionDataListListener>()
    private val dbHelper = SessionDBHelper(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        val data = dbHelper.selectAllOrderByTimestamp()
        sessionList.addAll(data)
    }

    fun createSession(canvasState: String): SessionData {
        val sessionName = generateSessionName()
        val id = dbHelper.insertSession(sessionName, canvasState)
        
        val sessionData = SessionData(
            id = id.toInt(),
            name = sessionName,
            canvasState = canvasState,
            timestamp = System.currentTimeMillis(),
            date = LocalDate.now()
        )
        
        sessionList = ArrayList(sessionList)
        sessionList.add(0, sessionData)
        notifyChanges()
        
        return sessionData
    }

    fun updateSession(id: Int, canvasState: String) {
        dbHelper.updateSession(id, canvasState)
        
        // Update in-memory list
        val index = sessionList.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = sessionList[index].copy(
                canvasState = canvasState,
                timestamp = System.currentTimeMillis()
            )
            sessionList = ArrayList(sessionList)
            sessionList[index] = updated
            notifyChanges()
        }
    }

    fun getSessionById(id: Int): SessionData? {
        return sessionList.find { it.id == id }
    }

    fun deleteSession(id: Int) {
        sessionList.removeIf { it.id == id }
        dbHelper.deleteById(id)
        notifyChanges()
    }

    fun clearAllSessions() {
        sessionList = ArrayList(sessionList)
        sessionList.clear()
        dbHelper.clearTable()
        notifyChanges()
    }

    fun addListener(listener: SessionDataListListener) {
        listeners.add(listener)
        // Notify immediately on main thread
        mainHandler.post {
            listener.invoke(sessionList)
        }
    }

    fun removeListener(listener: SessionDataListListener) {
        listeners.remove(listener)
    }

    private fun notifyChanges() {
        // Ensure notifications happen on the main thread
        mainHandler.post {
            listeners.forEach { it.invoke(sessionList) }
        }
    }

    private fun generateSessionName(): String {
        val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
        val dateStr = LocalDate.now().format(formatter)
        
        // Count sessions created today to generate unique number
        val today = LocalDate.now()
        val todaySessionCount = sessionList.count { it.date == today }
        val sessionNumber = todaySessionCount + 1
        
        return "Session N° $dateStr"
    }
}
