package com.dz.calculator.session

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

class SessionDBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 2  // Incremented for schema change
        private const val DATABASE_NAME = "sessions.db"

        private const val TABLE_NAME = "sessions"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_CANVAS_STATE = "canvas_state"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_CUSTOM_NAME = "custom_name"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_CANVAS_STATE TEXT,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_CUSTOM_NAME TEXT DEFAULT ''
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Add custom_name column if upgrading from version 1
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_CUSTOM_NAME TEXT DEFAULT ''")
        }
    }

    fun insertSession(name: String, canvasState: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_CANVAS_STATE, canvasState)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_CUSTOM_NAME, "")
        }
        val id = db.insert(TABLE_NAME, null, values)
        db.close()
        return id
    }

    fun updateSession(id: Int, canvasState: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CANVAS_STATE, canvasState)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        db.update(TABLE_NAME, values, "$COLUMN_ID=?", arrayOf(id.toString()))
        db.close()
    }
    
    fun updateSessionName(id: Int, customName: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CUSTOM_NAME, customName)
        }
        db.update(TABLE_NAME, values, "$COLUMN_ID=?", arrayOf(id.toString()))
        db.close()
    }

    fun selectAllOrderByTimestamp(): MutableList<SessionData> {
        val sessionList = mutableListOf<SessionData>()
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_TIMESTAMP DESC")

        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(COLUMN_ID)
            val nameIndex = it.getColumnIndexOrThrow(COLUMN_NAME)
            val canvasStateIndex = it.getColumnIndexOrThrow(COLUMN_CANVAS_STATE)
            val timestampIndex = it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
            val customNameIndex = it.getColumnIndex(COLUMN_CUSTOM_NAME)

            while (it.moveToNext()) {
                val id = it.getInt(idIndex)
                val name = it.getString(nameIndex)
                val canvasState = it.getString(canvasStateIndex)
                val timestamp = it.getLong(timestampIndex)
                val customName = if (customNameIndex >= 0) it.getString(customNameIndex) ?: "" else ""
                val localDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                val sessionData = SessionData(id, name, canvasState, timestamp, localDate, customName)
                sessionList.add(sessionData)
            }
        }

        db.close()
        return sessionList
    }

    fun deleteById(id: Int) {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_NAME WHERE $COLUMN_ID=?", arrayOf(id.toString()))
        db.close()
    }

    fun clearTable() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_NAME")
        db.close()
    }
}
