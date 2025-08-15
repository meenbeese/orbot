package org.torproject.android.ui.v3onionservice.clientauth

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ClientAuthDatabase internal constructor(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(V3_AUTHS_CREATE_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    companion object {
        const val DATABASE_NAME: String = "v3_client_auths"
        private const val DATABASE_VERSION = 1

        private const val V3_AUTHS_CREATE_SQL =
            "CREATE TABLE " +
                DATABASE_NAME +
                " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "domain TEXT, " +
                "hash TEXT, " +
                "enabled INTEGER DEFAULT 1);"
    }
}
