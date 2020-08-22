package loggy.sh

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import loggy.sh.loggy.BuildConfig
import sh.loggy.*
import timber.log.Timber
import java.net.URL
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
    private var instanceID: InstanceId? = null
    private var deviceID: DeviceId? = null

    suspend fun setup(application: Application, config: LoggyConfig) {

        val instance: Instance = Instance.newBuilder()
            .setAppid(config.appID)
            .setDeviceid(config.uniqueDeviceID)
            .build()

        val device: Device = Device.newBuilder()
            .setId(config.uniqueDeviceID)
            .putAllDetails(deviceInformation(application))
            .build()

        withContext(Dispatchers.IO) {
            try {
                instanceID = loggyService.insertInstance(instance)
                deviceID = loggyService.insertDevice(device)
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

    private fun deviceInformation(context: Context): Map<String, String> {
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

        return map
    }

    fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val instance = instanceID ?: return
        val level = when (priority) {
            Log.VERBOSE, Log.ASSERT, Log.DEBUG -> LoggyMessage.Level.DEBUG
            Log.ERROR -> LoggyMessage.Level.ERROR
            Log.WARN -> LoggyMessage.Level.WARN
            Log.INFO -> LoggyMessage.Level.INFO
            else -> LoggyMessage.Level.DEBUG
        }
        val exception = if (t != null) "\n ${t.message} ${t.cause} ${t.stackTrace}" else ""
        val msg = "${tag ?: ""} \n $message $exception"
        val loggyMessage = LoggyMessage
            .newBuilder()
            .setInstanceid(instance.id)
            .setLevel(level)
            .setMsg(msg)
            .setSessionid(sessionID)
            .setTimestamp(Timestamp.getDefaultInstance())
            .build()

        messageChannel.offer(loggyMessage)
    }

}