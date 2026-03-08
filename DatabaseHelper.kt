package com.example.smartwasteclassification

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "SmartWaste.db"
        private const val DATABASE_VERSION = 3 // Bumped version for history table
        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_NICKNAME = "nickname"

        private const val TABLE_HISTORY = "history"
        private const val COLUMN_HIST_ID = "hist_id"
        private const val COLUMN_HIST_TYPE = "type" // 0: Photo Identify, 1: Search
        private const val COLUMN_HIST_CONTENT = "content"
        private const val COLUMN_HIST_TIME = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createUsersTable = ("CREATE TABLE " + TABLE_USERS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_USERNAME + " TEXT UNIQUE,"
                + COLUMN_PASSWORD + " TEXT,"
                + COLUMN_NICKNAME + " TEXT" + ")")
        db?.execSQL(createUsersTable)

        val createHistoryTable = ("CREATE TABLE " + TABLE_HISTORY + "("
                + COLUMN_HIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_HIST_TYPE + " INTEGER,"
                + COLUMN_HIST_CONTENT + " TEXT,"
                + COLUMN_HIST_TIME + " TEXT" + ")")
        db?.execSQL(createHistoryTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db?.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN $COLUMN_NICKNAME TEXT")
        }
        if (oldVersion < 3) {
            val createHistoryTable = ("CREATE TABLE " + TABLE_HISTORY + "("
                    + COLUMN_HIST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_HIST_TYPE + " INTEGER,"
                    + COLUMN_HIST_CONTENT + " TEXT,"
                    + COLUMN_HIST_TIME + " TEXT" + ")")
            db?.execSQL(createHistoryTable)
        }
    }

    fun addUser(user: User): Long {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_USERNAME, user.username)
        values.put(COLUMN_PASSWORD, user.password)
        values.put(COLUMN_NICKNAME, user.nickname)
        val id = db.insert(TABLE_USERS, null, values)
        db.close()
        return id
    }

    fun getUser(username: String, password: String): User? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            arrayOf(COLUMN_ID, COLUMN_USERNAME, COLUMN_PASSWORD, COLUMN_NICKNAME),
            "$COLUMN_USERNAME=? AND $COLUMN_PASSWORD=?",
            arrayOf(username, password),
            null,
            null,
            null
        )

        var user: User? = null
        if (cursor != null && cursor.moveToFirst()) {
            user = User(
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NICKNAME)) ?: ""
            )
            cursor.close()
        }
        db.close()
        return user
    }

    fun updateNickname(username: String, newNickname: String): Int {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_NICKNAME, newNickname)
        val rows = db.update(TABLE_USERS, values, "$COLUMN_USERNAME=?", arrayOf(username))
        db.close()
        return rows
    }

    fun addHistory(type: Int, content: String, timestamp: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_HIST_TYPE, type)
        values.put(COLUMN_HIST_CONTENT, content)
        values.put(COLUMN_HIST_TIME, timestamp)
        db.insert(TABLE_HISTORY, null, values)
        db.close()
    }

    fun getHistoryByType(type: Int): List<String> {
        val list = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            arrayOf(COLUMN_HIST_CONTENT, COLUMN_HIST_TIME),
            "$COLUMN_HIST_TYPE = ?",
            arrayOf(type.toString()),
            null, null, "$COLUMN_HIST_ID DESC"
        )
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HIST_CONTENT))
                val time = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HIST_TIME))
                list.add("[$time] $content")
            } while (cursor.moveToNext())
            cursor.close()
        }
        db.close()
        return list
    }

    fun getAllHistory(): List<String> {
        val list = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            arrayOf(COLUMN_HIST_TYPE, COLUMN_HIST_CONTENT, COLUMN_HIST_TIME),
            null, null, null, null, "$COLUMN_HIST_ID DESC"
        )
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HIST_TYPE))
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HIST_CONTENT))
                val time = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HIST_TIME))
                val typeStr = if (type == 0) "拍照识别" else "分类搜索"
                list.add("[$time] $typeStr: $content")
            } while (cursor.moveToNext())
            cursor.close()
        }
        db.close()
        return list
    }
}
