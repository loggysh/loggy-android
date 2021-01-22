package loggy.sh

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import loggy.sh.loggy.BuildConfig
import sh.loggy.Device
import sh.loggy.LoggyServiceGrpcKt
import sh.loggy.Message
import sh.loggy.Session
import timber.log.Timber
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.system.exitProcess

object Loggy {

    private val loggyService by lazy { LoggyServiceGrpcKt.LoggyServiceCoroutineStub(channel) }

    private val url = URL(BuildConfig.loggyUrl)
    private val port = if (url.port == -1) url.defaultPort else url.port

    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(url.host, port)
        .apply {
            Timber.i("Connecting to ${url.host}:$port")
            if (url.protocol == "https") {
                useTransportSecurity()
            } else {
                usePlaintext()
            }
        }.build()

    private val messageChannel = BroadcastChannel<Message>(Channel.BUFFERED)

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var sessionID: Int = -1
    private var deviceID: String? = null
    private var feature: String? = null

    suspend fun setup(application: Application, userID: String, deviceName: String = "") {
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            log(100, "Thread: ${thread.name}", "Failed", e)
            exitProcess(1)
        }

        withContext(Dispatchers.IO) {
            deviceID = getDeviceId(application)

            val packageName = application.packageName
            val appID = "$userID/$packageName"
            val stringId = application.applicationInfo.labelRes
            val appName =
                if (stringId == 0) application.applicationInfo.nonLocalizedLabel.toString() else application.getString(
                    stringId
                )

            try {
                Log.d("Loggy", "Register For Application")
                val app = loggyService.getOrInsertApplication(
                    sh.loggy.Application.newBuilder()
                        .setIcon("")
                        .setId(appID)
                        .setName(appName)
                        .build()
                )

                if (deviceID == null) {
                    deviceID = UUID.randomUUID().toString()
                }

                Log.d("Loggy", "Register New Device")
                val device = loggyService.getOrInsertDevice(
                    Device.newBuilder()
                        .setId(deviceID)
                        .setDetails(deviceInformation(application, deviceName))
                        .build()
                )
                deviceID = device.id
                saveDevice(application, device.id)

                Log.d("Loggy", "Register For Session")
                val session = loggyService.insertSession(
                    Session.newBuilder()
                        .setAppid(app.id)
                        .setDeviceid(deviceID)
                        .build()
                )

                sessionID = session.id
                Log.d("Loggy", "Register Send")
                loggyService.registerSend(session)

                startListeningForMessages()
            } catch (e: Exception) {
                Log.e("Loggy", "Failed to setup loggy", e)
            }
        }
    }

    private fun startListeningForMessages() {
        scope.launch {
            try {
                loggyService.send(messageChannel.asFlow())
            } catch (e: Exception) {
                Log.e("Loggy", "Failed to send message", e)
            }
        }
    }

    fun startSession() {
    }

    fun endSession() {
    }

    fun startFeature(value: String) {
        feature = value
    }

    fun endFeature(value: String) {
        feature = ""
    }

    private fun deviceInformation(context: Context, deviceName: String): String {
        val map: MutableMap<String, String> = mutableMapOf()

        try {
            map[deviceUniqueName] = deviceName
            val applicationInfo = context.applicationInfo
            val stringId = applicationInfo.labelRes
            map[appName] =
                if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else context.getString(
                    stringId
                )

            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            map[appVersion] = pInfo.versionName
            map[androidOSVersion] =
                "${System.getProperty("os.version")}(${Build.VERSION.INCREMENTAL})"
            map[androidAPILevel] = "${Build.VERSION.SDK_INT}"
            map[deviceType] = Build.DEVICE
            map[deviceModel] = "${Build.MODEL} ${Build.PRODUCT}"
        } catch (e: Exception) {
            Log.e("Loggy", "Failed", e)
        }

        val jsonMap: Map<String, JsonElement> = map.mapValues { s -> JsonPrimitive(s.value) }
        return Json { isLenient = true }.encodeToString(
            JsonObject.serializer(),
            JsonObject(jsonMap)
        )
    }

    fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.VERBOSE, Log.ASSERT, Log.DEBUG -> Message.Level.DEBUG
            Log.ERROR -> Message.Level.ERROR
            Log.WARN -> Message.Level.WARN
            Log.INFO -> Message.Level.INFO
            100 -> Message.Level.CRASH
            else -> Message.Level.DEBUG
        }
        val exception = if (t != null) "\n ${t.message} ${t.cause} ${t.stackTrace}" else ""
        val msg = "${feature ?: ""} ${tag ?: ""} \n $message $exception"
        val time: Instant = Instant.now().atZone(ZoneId.of("UTC")).toInstant()
        val timestamp = Timestamp.newBuilder().setSeconds(time.epochSecond)
            .setNanos(time.nano).build()

        val loggyMessage = Message
            .newBuilder()
            .setLevel(level)
            .setMsg(msg)
            .setSessionid(sessionID)
            .setTimestamp(timestamp)
            .build()

        Log.d("Loggy ${sessionID} State: ${channel.getState(false)}", message)
        messageChannel.offer(loggyMessage)
    }

    private fun saveInstance(context: Context, instanceId: String) {
        val preferences = context.getSharedPreferences("loggy", Context.MODE_PRIVATE)
        preferences.edit().putString("instance_id", instanceId).apply()
    }

    private fun getInstanceId(context: Context): String? {
        val preferences = context.getSharedPreferences("loggy", Context.MODE_PRIVATE)
        return preferences.getString("instance_id", null)
    }

    private fun getDeviceId(context: Context): String? {
        val preferences = context.getSharedPreferences("loggy", Context.MODE_PRIVATE)
        return preferences.getString("device_id", null)
    }

    private fun saveDevice(context: Context, deviceId: String) {
        val preferences = context.getSharedPreferences("loggy", Context.MODE_PRIVATE)
        preferences.edit().putString("device_id", deviceId).apply()
    }

}