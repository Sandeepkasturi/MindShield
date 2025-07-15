package com.example.mindshield.data.local

import androidx.room.*
import com.example.mindshield.data.model.AppInfoEntity

@Dao
interface AppInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppInfo(appInfo: AppInfoEntity)

    @Update
    suspend fun updateAppInfo(appInfo: AppInfoEntity)

    @Query("SELECT * FROM app_info")
    suspend fun getAllAppInfo(): List<AppInfoEntity>

    @Query("SELECT * FROM app_info WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppInfoByPackage(packageName: String): AppInfoEntity?

    @Delete
    suspend fun deleteAppInfo(appInfo: AppInfoEntity)
} 