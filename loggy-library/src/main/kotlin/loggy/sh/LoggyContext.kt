package loggy.sh

import sh.loggy.internal.Application
import sh.loggy.internal.Device

interface LoggyContext {
    suspend fun saveApiKey(apiKey: String)
    suspend fun getApiKey(): String
    suspend fun getApplication(): Application
    suspend fun saveApplicationID(appID: String)
    suspend fun getApplicationID(): String
    suspend fun getDevice(appID: String): Device
    suspend fun generateAndSaveDeviceID()
    suspend fun getDeviceID(): String
    suspend fun saveIdentity(userId: String?, email: String?, userName: String?)
    fun getDeviceHash(appID: String, deviceID: String): String
}
