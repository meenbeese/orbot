package org.torproject.android.ui.v3onionservice

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class OnionServiceDatabase internal constructor(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ONION_SERVICES_CREATE_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (newVersion > oldVersion) {
            db.execSQL("ALTER TABLE $ONION_SERVICE_TABLE_NAME ADD COLUMN filepath text")
        }
    }

    companion object {
        const val DATABASE_NAME = "onion_service"
        const val ONION_SERVICE_TABLE_NAME = "onion_services"
        private const val DATABASE_VERSION = 2
        private const val ONION_SERVICES_CREATE_SQL =
            "CREATE TABLE " + ONION_SERVICE_TABLE_NAME + " (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, " +
                    "domain TEXT, " +
                    "onion_port INTEGER, " +
                    "created_by_user INTEGER DEFAULT 0, " +
                    "enabled INTEGER DEFAULT 1, " +
                    "port INTEGER, " +
                    "filepath TEXT);"
    }
}
