package loggy.sh

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import kotlinx.coroutines.flow.firstOrNull
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
import sh.loggy.internal.Device
import java.util.*
import sh.loggy.internal.Application as LoggyApp

class LoggyContextForAndroid(
    private val application: Application
) : LoggyContext {

    override suspend fun saveApiKey(apiKey: String) {
        application.settingsDataStore.updateData { settings ->
            settings.toBuilder().setApiKey(apiKey).build()
        }
    }

    override suspend fun getApiKey(): String {
        return application.settingsDataStore.data.firstOrNull()?.apiKey ?: ""
    }

    override suspend fun getApplication(): LoggyApp {
        val appName = if (application.applicationInfo.labelRes == 0) {
            application.applicationInfo.nonLocalizedLabel.toString()
        } else {
            application.getString(application.applicationInfo.labelRes)
        }

        return LoggyApp.newBuilder()
            .setIcon("")
            .setPackagename(application.packageName)
            .setName(appName)
            .build()
    }

    override suspend fun saveApplicationID(appID: String) {
        application.settingsDataStore.updateData { settings ->
            settings.toBuilder().setAppId(appID).build()
        }
    }

    override suspend fun getApplicationID(): String {
        return application.settingsDataStore.data.firstOrNull()?.appId ?: ""
    }

    override suspend fun getDevice(appID: String): Device {
        return Device.newBuilder()
            .setId(getDeviceID())
            .setAppid(appID)
            .setDetails(deviceInformation(application))
            .build()
    }

    override suspend fun generateAndSaveDeviceID() {
        val deviceID = UUID.randomUUID().toString()
        application.settingsDataStore.updateData { settings ->
            settings.toBuilder().setDeviceId(deviceID).build()
        }
    }

    override suspend fun saveIdentity(userId: String?, email: String?, userName: String?) {
        application.settingsDataStore.updateData { settings ->
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
        return "$appHash-$deviceHash"
    }

    override suspend fun getDeviceID(): String {
        return application.settingsDataStore.data.firstOrNull()?.deviceId ?: ""
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
            SupportLogs.error("Failed to get Device Information", e)
        }

        val jsonMap: Map<String, JsonElement> = map.mapValues { s -> JsonPrimitive(s.value) }
        return Json { isLenient = true }.encodeToString(
            JsonObject.serializer(),
            JsonObject(jsonMap)
        )
    }
}