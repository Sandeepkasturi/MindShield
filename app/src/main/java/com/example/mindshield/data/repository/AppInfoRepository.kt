package com.example.mindshield.data.repository

import com.example.mindshield.data.local.AppInfoDao
import com.example.mindshield.data.model.AppInfoEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInfoRepository @Inject constructor(private val appInfoDao: AppInfoDao) {
    suspend fun insertAppInfo(appInfo: AppInfoEntity) = appInfoDao.insertAppInfo(appInfo)
    suspend fun updateAppInfo(appInfo: AppInfoEntity) = appInfoDao.updateAppInfo(appInfo)
    suspend fun getAllAppInfo(): List<AppInfoEntity> = appInfoDao.getAllAppInfo()
    suspend fun getAppInfoByPackage(packageName: String): AppInfoEntity? = appInfoDao.getAppInfoByPackage(packageName)
    suspend fun deleteAppInfo(appInfo: AppInfoEntity) = appInfoDao.deleteAppInfo(appInfo)
} 