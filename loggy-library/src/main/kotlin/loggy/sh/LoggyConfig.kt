package loggy.sh

const val appName = "application_name"
const val appVersion = "application_version"
const val deviceModel = "device_model"
const val deviceType = "device_type"
const val androidOSVersion = "android_os_version"
const val androidAPILevel = "android_api_level"

data class LoggyConfig(
    val appID: String,
    val uniqueDeviceID: String
)