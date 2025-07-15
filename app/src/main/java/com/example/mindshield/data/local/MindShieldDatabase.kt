package com.example.mindshield.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.mindshield.data.model.AppUsage
import com.example.mindshield.data.model.DistractionEvent
import com.example.mindshield.data.model.AppInfoEntity

@Database(
    entities = [AppUsage::class, DistractionEvent::class, AppInfoEntity::class],
    version = 2, // Incremented version from 1 to 2
    exportSchema = false
)
abstract class MindShieldDatabase : RoomDatabase() {
    
    abstract fun appUsageDao(): AppUsageDao
    abstract fun distractionEventDao(): DistractionEventDao
    abstract fun appInfoDao(): AppInfoDao
    
    companion object {
        @Volatile
        private var INSTANCE: MindShieldDatabase? = null
        
        fun getDatabase(context: Context): MindShieldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MindShieldDatabase::class.java,
                    "mindshield_database"
                )
                .fallbackToDestructiveMigration() // Allow destructive migration for dev
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 