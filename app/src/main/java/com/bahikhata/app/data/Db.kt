package com.bahikhata.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "bahikhata.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE parties(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "phone TEXT," +
                "type TEXT NOT NULL," +
                "opening REAL NOT NULL DEFAULT 0," +
                "note TEXT," +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE entries(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "party_id INTEGER NOT NULL," +
                "gave INTEGER NOT NULL," +
                "amount REAL NOT NULL," +
                "mode TEXT," +
                "note TEXT," +
                "date INTEGER NOT NULL," +
                "due_date INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE cash(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "cash_in INTEGER NOT NULL," +
                "amount REAL NOT NULL," +
                "category TEXT," +
                "note TEXT," +
                "mode TEXT," +
                "date INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE items(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "unit TEXT," +
                "qty REAL NOT NULL DEFAULT 0," +
                "purchase_price REAL NOT NULL DEFAULT 0," +
                "sell_price REAL NOT NULL DEFAULT 0," +
                "low_stock REAL NOT NULL DEFAULT 5)"
        )
        db.execSQL(
            "CREATE TABLE invoices(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "number TEXT," +
                "party_name TEXT," +
                "phone TEXT," +
                "date INTEGER NOT NULL," +
                "lines TEXT," +
                "gst REAL NOT NULL DEFAULT 0," +
                "discount REAL NOT NULL DEFAULT 0," +
                "paid INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema is stable in v1. Future migrations go here.
    }
}
