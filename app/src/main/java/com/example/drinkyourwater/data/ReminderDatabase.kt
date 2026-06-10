package com.example.drinkyourwater.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Medicine::class, WaterReminder::class, ReminderHistory::class], version = 10, exportSchema = false)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var Instance: ReminderDatabase? = null

        fun getDatabase(context: Context): ReminderDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, ReminderDatabase::class.java, "reminder_database")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
