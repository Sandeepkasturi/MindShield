package com.example.mindshield.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.mindshield.data.model.AppUsage
import com.example.mindshield.data.model.DistractionEvent
import com.example.mindshield.data.model.AppInfoEntity
import com.example.mindshield.data.model.AppTimer
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppInfoEntity::class],
    version = 3, // Incremented version from 2 to 3
    exportSchema = false
)
abstract class MindShieldDatabase : RoomDatabase() {
    
    // abstract fun appUsageDao(): AppUsageDao
    // abstract fun distractionEventDao(): DistractionEventDao
    abstract fun appInfoDao(): AppInfoDao
    // abstract fun appTimerDao(): AppTimerDao
    
    companion object {
        @Volatile
        private var INSTANCE: MindShieldDatabase? = null

        // Migration from version 2 to 3: add currentUsageSeconds column
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_timers ADD COLUMN currentUsageSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        fun getDatabase(context: Context): MindShieldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MindShieldDatabase::class.java,
                    "mindshield_database"
                )
                .addMigrations(MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 