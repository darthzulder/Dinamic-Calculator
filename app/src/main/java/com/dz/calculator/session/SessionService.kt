package com.dz.calculator.session

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.dz.calculator.R
import java.time.LocalDate

typealias SessionDataListListener = (sessionList: List<SessionData>) -> Unit

class SessionService(private val context: Context) {
    private var sessionList = mutableListOf<SessionData>()
    private val listeners = mutableSetOf<SessionDataListListener>()
    private val dbHelper = SessionDBHelper(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences =
            context.getSharedPreferences(
                    context.getString(R.string.session_prefs),
                    Context.MODE_PRIVATE
            )

    init {
        val data = dbHelper.selectAllOrderByTimestamp()
        sessionList.addAll(data)
    }

    fun createSession(canvasState: String): SessionData {
        val sessionName = generateSessionName()
        val id = dbHelper.insertSession(sessionName, canvasState)

        val sessionData =
                SessionData(
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
            val updated =
                    sessionList[index].copy(
                            canvasState = canvasState,
                            timestamp = System.currentTimeMillis()
                    )
            sessionList = ArrayList(sessionList)
            sessionList[index] = updated
            notifyChanges()
        }
    }

    fun updateSessionName(id: Int, customName: String) {
        dbHelper.updateSessionName(id, customName)

        // Update in-memory list
        val index = sessionList.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = sessionList[index].copy(customName = customName)
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
        mainHandler.post { listener.invoke(sessionList) }
    }

    fun removeListener(listener: SessionDataListListener) {
        listeners.remove(listener)
    }

    private fun notifyChanges() {
        // Ensure notifications happen on the main thread
        mainHandler.post { listeners.forEach { it.invoke(sessionList) } }
    }

    private fun generateSessionName(): String {
        // Obtener y incrementar el contador de sesiones
        val sessionCounter = prefs.getInt(context.getString(R.string.session_counter_key), 0) + 1
        prefs.edit().putInt(context.getString(R.string.session_counter_key), sessionCounter).apply()

        // Obtener la hora actual en formato HH:MM
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val timeStr = String.format("%02d:%02d", hour, minute)

        return context.getString(R.string.session_name_format, sessionCounter, timeStr)
    }
}
