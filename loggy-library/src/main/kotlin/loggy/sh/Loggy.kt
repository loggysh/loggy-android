package loggy.sh

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import loggy.sh.loggy.BuildConfig
import sh.loggy.Device
import sh.loggy.Instance
import sh.loggy.LoggyMessage
import sh.loggy.LoggyServiceGrpcKt
import timber.log.Timber
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.*

object Loggy {

    private val loggyService by lazy { LoggyServiceGrpcKt.LoggyServiceCoroutineStub(channel()) }

    private fun channel(): ManagedChannel {
        val url = URL(BuildConfig.loggyUrl)
        val port = if (url.port == -1) url.defaultPort else url.port

        Timber.i("Connecting to ${url.host}:$port")

        val builder = ManagedChannelBuilder.forAddress(url.host, port)
        if (url.protocol == "https") {
            builder.useTransportSecurity()
        } else {
            builder.usePlaintext()
        }

        return builder.executor(Dispatchers.Default.asExecutor()).build()
    }

    private val messageChannel = BroadcastChannel<LoggyMessage>(Channel.BUFFERED)

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var sessionID: String = ""
    private var instanceID: String? = null
    private var deviceID: String? = null

    suspend fun setup(application: Application, config: LoggyConfig) {

        withContext(Dispatchers.IO) {
            instanceID = getInstanceId(application)
            deviceID = getDeviceId(application)

            try {
                if (instanceID == null) {

                } else {
                    // Instance Id is unique. Once created. Save and restore.
                }

                if (deviceID == null) {
                    Log.d("Loggy", "Register New Device")
                    val device: Device = Device.newBuilder()
                        .setId(config.uniqueDeviceID)
                        .setDetails(deviceInformation(application))
                        .build()

                    loggyService.insertDevice(device)
                        .runCatching {
                            deviceID = this.id
                            saveDevice(application, this.id)
                        }
                        .onFailure {
                            // Device already exists. Ignore
                        }
                } else {
                    Log.d("Loggy", "Device exists $deviceID")
                    // Device Id is unique. Once created. Save and restore.
                }


                val instance: Instance = Instance.newBuilder()
                    .setAppid(config.appID)
                    .setDeviceid(deviceID)
                    .build()

                Log.d("Loggy", "Get or create instance")
                loggyService.getOrInsertInstance(instance)
                    .runCatching {
                        Log.d("Loggy", "Start Register Send")
                        loggyService.registerSend(this)
                            .runCatching {
                                Log.d("Loggy", "Register Send")
                            }
                        delay(1000)
                        instanceID = this.id
                        saveInstance(application, this.id)
                    }
                    .onFailure {

                    }
            } catch (e: Exception) {
                Log.e("Loggy", "Failed to setup loggy", e)
            }
        }
    }

    fun startSession() {
        sessionID = UUID.randomUUID().toString()
        scope.launch {
            try {
                loggyService.send(messageChannel.asFlow())
            } catch (e: Exception) {
                Log.e("Loggy", "Failed to send message", e)
            }
        }
    }

    fun endSession() {
        sessionID = ""
    }

    private fun deviceInformation(context: Context): String {
        val map: MutableMap<String, String> = mutableMapOf()

        try {
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.VERBOSE, Log.ASSERT, Log.DEBUG -> LoggyMessage.Level.DEBUG
            Log.ERROR -> LoggyMessage.Level.ERROR
            Log.WARN -> LoggyMessage.Level.WARN
            Log.INFO -> LoggyMessage.Level.INFO
            else -> LoggyMessage.Level.DEBUG
        }
        val exception = if (t != null) "\n ${t.message} ${t.cause} ${t.stackTrace}" else ""
        val msg = "${tag ?: "TAG"} \n $message $exception"
        val time: Instant = Instant.now().atZone(ZoneId.of("UTC")).toInstant()
        val timestamp = Timestamp.newBuilder().setSeconds(time.epochSecond)
            .setNanos(time.nano).build()

        val loggyMessage = LoggyMessage
            .newBuilder()
            .setInstanceid(instanceID)
            .setLevel(level)
            .setMsg(msg)
            .setSessionid(sessionID)
            .setTimestamp(timestamp)
            .build()

        Log.d("Loggy ${instanceID}", message)
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