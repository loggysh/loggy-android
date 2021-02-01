package loggy.sh

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import loggy.sh.utils.Hashids
import sh.loggy.Device
import timber.log.Timber
import java.util.*
import sh.loggy.Application as LoggyApp

private const val appName = "application_name"
private const val appVersion = "application_version"
private const val deviceModel = "device_model"
private const val deviceName = "device_name"
private const val deviceType = "device_type"
private const val androidOSVersion = "android_os_version"
private const val androidAPILevel = "android_api_level"

class LoggyContextForAndroid(
    private val application: Application,
    private val userID: String,
    private val dName: String
) : LoggyContext {

    private val applicationID: String by lazy { "$userID/${application.packageName}" }

    override fun getApplication(): LoggyApp {
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

    override fun getDevice(): Device {
        return Device.newBuilder()
            .setId(getDeviceID(application))
            .setDetails(deviceInformation(application))
            .build()
    }

    override fun getDeviceHash(appID: String, deviceID: String): String {
        val appHash = Hashids(appID, 6).encode(1, 2, 3)
        val deviceHash = Hashids(deviceID, 6).encode(4, 4, 4)
        return "$appHash/$deviceHash"
    }

    private fun getDeviceID(context: Context): String {
        val preferences = context.getSharedPreferences("loggy", Context.MODE_PRIVATE)
        var deviceId = preferences.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().apply {
                Log.d("Loggy", "Save New ID")
                saveDevice(context, this)
            }
        }
        return deviceId
    }

    private fun saveDevice(context: Context, deviceId: String) {
        val preferences = context.getSharedPreferences("loggy", Context.MODE_PRIVATE)
        preferences.edit().putString("device_id", deviceId).apply()
    }

    private fun deviceInformation(context: Context): String {
        val map: MutableMap<String, String> = mutableMapOf()

        try {
            map[deviceName] = dName
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
            Timber.e(e, "Loggy Failed")
        }

        val jsonMap: Map<String, JsonElement> = map.mapValues { s -> JsonPrimitive(s.value) }
        return Json { isLenient = true }.encodeToString(
            JsonObject.serializer(),
            JsonObject(jsonMap)
        )
    }
}