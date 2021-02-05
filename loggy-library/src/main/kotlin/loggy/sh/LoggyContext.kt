package loggy.sh

import sh.loggy.Application
import sh.loggy.Device

interface LoggyContext {
    suspend fun getApplication(): Application
    suspend fun getDevice(): Device
    fun getDeviceHash(appID: String, deviceID: String): String
}
