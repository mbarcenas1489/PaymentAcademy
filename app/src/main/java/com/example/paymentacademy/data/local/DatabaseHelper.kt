package com.example.paymentacademy.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    "paymentacademy.db",
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE members (
                id TEXT PRIMARY KEY,
                organization_id TEXT,
                full_name TEXT,
                document_number TEXT,
                email TEXT,
                phone TEXT,
                status TEXT,
                joined_on TEXT,
                notes TEXT,
                created_at TEXT,
                updated_at TEXT,
                sync_pending INTEGER DEFAULT 0
            )
        """)
        
        db.execSQL("""
            CREATE TABLE payments (
                id TEXT PRIMARY KEY,
                organization_id TEXT,
                member_id TEXT,
                amount REAL,
                paid_on TEXT,
                payment_type TEXT,
                payment_method TEXT,
                valid_from TEXT,
                valid_until TEXT,
                reference TEXT,
                notes TEXT,
                created_at TEXT,
                sync_pending INTEGER DEFAULT 0,
                is_deleted INTEGER DEFAULT 0
            )
        """)

        createOrganizationIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createOrganizationIndexes(db)
    }

    fun clearAllData() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("payments", null, null)
            writableDatabase.delete("members", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun createOrganizationIndexes(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS members_organization_idx ON members(organization_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS payments_organization_idx ON payments(organization_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS payments_organization_member_idx " +
                "ON payments(organization_id, member_id)",
        )
    }

    private companion object {
        const val DATABASE_VERSION = 2
    }
}
