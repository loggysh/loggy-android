package loggy.sh

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.createDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import loggy.sh.DeviceProperties.androidAPILevel
import loggy.sh.DeviceProperties.androidOSVersion
import loggy.sh.DeviceProperties.appName
import loggy.sh.DeviceProperties.appVersion
import loggy.sh.DeviceProperties.deviceModel
import loggy.sh.DeviceProperties.deviceName
import loggy.sh.DeviceProperties.deviceType
import loggy.sh.utils.Hashids
import loggy.sh.utils.SettingsSerializer
import sh.loggy.Device
import sh.loggy.LoggySettings
import timber.log.Timber
import java.util.*
import sh.loggy.Application as LoggyApp

class LoggyContextForAndroid(
    private val application: Application,
    private val clientID: String
) : LoggyContext {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val applicationID: String by lazy { "$clientID/${application.packageName}" }

    private val settingsDataStore: DataStore<LoggySettings.Settings> by lazy {
        application.createDataStore(
            fileName = "settings.pb",
            serializer = SettingsSerializer
        )
    }

    init {
        scope.launch {
            saveApplicationID(applicationID)
        }
    }

    override suspend fun getApplication(): LoggyApp {
        val appName = if (application.applicationInfo.labelRes == 0) {
            application.applicationInfo.nonLocalizedLabel.toString()
        } else {
            application.getString(application.applicationInfo.labelRes)
        }

        return LoggyApp.newBuilder()
            .setIcon("")
            .setId(applicationID)
            .setName(appName)
            .build()
    }

    override suspend fun saveApplicationID(appID: String) {
        settingsDataStore.updateData { settings ->
            settings.toBuilder().setAppId(appID).build()
        }
    }

    override suspend fun getApplicationID(): String {
        return settingsDataStore.data.firstOrNull()?.appId ?: applicationID
    }

    override suspend fun getDevice(): Device {
        return Device.newBuilder()
            .setId(getDeviceID())
            .setDetails(deviceInformation(application))
            .build()
    }

    override suspend fun saveDeviceID(deviceID: String) {
        Log.d(LOGGY_TAG, "Save Device ID")
        settingsDataStore.updateData { settings ->
            settings.toBuilder().setDeviceId(deviceID).build()
        }
    }

    override suspend fun saveIdentity(userId: String?, email: String?, userName: String?) {
        settingsDataStore.updateData { settings ->
            val sUserId = userId ?: settings.userId
            val sEmail = email ?: settings.email
            val sUserName = userName ?: settings.userName

            settings.toBuilder()
                .setUserId(sUserId)
                .setEmail(sEmail)
                .setUserName(sUserName)
                .build()
        }
    }

    override fun getDeviceHash(appID: String, deviceID: String): String {
        val appHash = Hashids(appID, 6).encode(1, 2, 3)
        val deviceHash = Hashids(deviceID, 6).encode(4, 4, 4)
        return "$appHash/$deviceHash"
    }

    override suspend fun getDeviceID(): String {
        var deviceId = settingsDataStore.data.firstOrNull()?.deviceId
        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            saveDeviceID(deviceId)
        }
        return deviceId
    }

    private fun deviceInformation(context: Context): String {
        val map: MutableMap<String, String> = mutableMapOf()

        try {
            map[deviceName] = ""
            val applicationInfo = context.applicationInfo
            val stringId = applicationInfo.labelRes
            map[appName] = if (stringId == 0) {
                applicationInfo.nonLocalizedLabel.toString()
            } else {
                context.getString(stringId)
            }

            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            map[appVersion] = pInfo.versionName
            map[androidOSVersion] =
                "${System.getProperty("os.version")}(${Build.VERSION.INCREMENTAL})"
            map[androidAPILevel] = "${Build.VERSION.SDK_INT}"
            map[deviceType] = Build.DEVICE
            map[deviceModel] = "${Build.MODEL} ${Build.PRODUCT}"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Device Information")
        }

        val jsonMap: Map<String, JsonElement> = map.mapValues { s -> JsonPrimitive(s.value) }
        return Json { isLenient = true }.encodeToString(
            JsonObject.serializer(),
            JsonObject(jsonMap)
        )
    }
}