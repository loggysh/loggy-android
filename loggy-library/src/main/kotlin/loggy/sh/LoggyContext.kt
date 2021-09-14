package loggy.sh

import sh.loggy.internal.Application
import sh.loggy.internal.Device

interface LoggyContext {
    suspend fun getApplication(): Application
    suspend fun saveApplicationID(appID: String)
    suspend fun getApplicationID(): String
    suspend fun getDevice(appID: String): Device
    suspend fun saveDeviceID(deviceID: String)
    suspend fun getDeviceID(): String
    suspend fun saveIdentity(userId: String?, email: String?, userName: String?)
    fun getDeviceHash(appID: String, deviceID: String): String
}
