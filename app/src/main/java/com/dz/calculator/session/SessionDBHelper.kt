package com.dz.calculator.session

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.ZoneId

class SessionDBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "sessions.db"

        private const val TABLE_NAME = "sessions"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_CANVAS_STATE = "canvas_state"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_CANVAS_STATE TEXT,
                $COLUMN_TIMESTAMP INTEGER
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertSession(name: String, canvasState: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_CANVAS_STATE, canvasState)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
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

    fun selectAllOrderByTimestamp(): MutableList<SessionData> {
        val sessionList = mutableListOf<SessionData>()
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_TIMESTAMP DESC")

        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(COLUMN_ID)
            val nameIndex = it.getColumnIndexOrThrow(COLUMN_NAME)
            val canvasStateIndex = it.getColumnIndexOrThrow(COLUMN_CANVAS_STATE)
            val timestampIndex = it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)

            while (it.moveToNext()) {
                val id = it.getInt(idIndex)
                val name = it.getString(nameIndex)
                val canvasState = it.getString(canvasStateIndex)
                val timestamp = it.getLong(timestampIndex)
                val localDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                val sessionData = SessionData(id, name, canvasState, timestamp, localDate)
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
