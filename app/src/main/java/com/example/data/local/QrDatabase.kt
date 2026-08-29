package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [QrHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class QrDatabase : RoomDatabase() {

    abstract fun qrHistoryDao(): QrHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: QrDatabase? = null

        fun getInstance(context: Context): QrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QrDatabase::class.java,
                    "qr_studio_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
