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
                sync_pending INTEGER DEFAULT 0,
                is_deleted INTEGER DEFAULT 0
            )
        """)
        
        db.execSQL("""
            CREATE TABLE payments (
                id TEXT PRIMARY KEY,
                organization_id TEXT,
                member_id TEXT,
                amount REAL,
                pending_amount REAL DEFAULT 0,
                currency TEXT DEFAULT 'NIO',
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

        createAppConfigurationTable(db)
        createMemberConfigurationTable(db)
        createOrganizationIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createOrganizationIndexes(db)
        if (oldVersion < 3) {
            runCatching {
                db.execSQL("ALTER TABLE payments ADD COLUMN pending_amount REAL DEFAULT 0")
            }
            createAppConfigurationTable(db)
        }
        if (oldVersion < 4) {
            runCatching {
                db.execSQL("ALTER TABLE payments ADD COLUMN pending_amount REAL DEFAULT 0")
            }
            runCatching {
                db.execSQL("ALTER TABLE payments ADD COLUMN currency TEXT DEFAULT 'NIO'")
            }
            createAppConfigurationTable(db)
        }
        if (oldVersion < 5) {
            runCatching {
                db.execSQL("ALTER TABLE members ADD COLUMN is_deleted INTEGER DEFAULT 0")
            }
            createMemberConfigurationTable(db)
        }
    }

    fun clearAllData() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("payments", null, null)
            writableDatabase.delete("members", null, null)
            writableDatabase.delete("app_configuration", null, null)
            writableDatabase.delete("member_configuration", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun createAppConfigurationTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS app_configuration (
                setting_name TEXT PRIMARY KEY,
                value_json TEXT
            )
        """)
    }

    private fun createMemberConfigurationTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS member_configuration (
                member_id TEXT PRIMARY KEY,
                setting_name TEXT DEFAULT 'PlaymentSetting',
                amount_month_cordoba REAL,
                amount_month_dollar REAL,
                sync_pending INTEGER DEFAULT 0,
                is_deleted INTEGER DEFAULT 0
            )
        """)
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
        const val DATABASE_VERSION = 5
    }
}
