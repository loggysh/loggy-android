package loggy.sh

import android.app.Application
import android.util.Log
import com.google.protobuf.Timestamp
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import loggy.sh.loggy.BuildConfig
import sh.loggy.LoggyServiceGrpcKt
import sh.loggy.Message
import timber.log.Timber
import java.net.URL
import java.time.Instant
import java.time.ZoneId

const val LOGGY_TAG = "loggy.sh"

private interface LoggyInterface {
    fun setup(application: Application, userID: String, deviceName: String)
    fun log(priority: Int, tag: String?, message: String, t: Throwable?)
}

object Loggy : LoggyInterface {

    private val loggyImpl: LoggyImpl by lazy { LoggyImpl() }

    override fun setup(application: Application, userID: String, deviceName: String) {
        loggyImpl.setup(application, userID, deviceName)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        loggyImpl.log(priority, tag, message, t)
    }
}

private class LoggyImpl : LoggyInterface {
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

    private val loggyService by lazy { LoggyServiceGrpcKt.LoggyServiceCoroutineStub(channel) }

    private val messageChannel = BroadcastChannel<Message>(Channel.BUFFERED)

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var sessionID: Int = -1
    private var feature: String? = null

    private lateinit var logRepository: LogRepository

    override fun setup(application: Application, userID: String, deviceName: String) {
        logRepository = LogRepository(application)

        installExceptionHandler()

        val loggyContext = LoggyContextForAndroid(application, userID, deviceName)

        try {
            scope.launch {
                val (sessionId, deviceId) = LoggyClient(loggyService).createSession(loggyContext)
                sessionID = sessionId
                startListeningForMessages()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup loggy")
        }
    }

    private fun installExceptionHandler() { // TODO Platform dependent
        val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            log(100, "Thread: ${thread.name}", e.message ?: "Unknown Message", e)
            defaultUncaughtExceptionHandler?.uncaughtException(thread, e) // Thanks Ragunath.
        }
    }

    private fun startListeningForMessages() {
        scope.launch {
            try {
                loggyService.send(messageChannel.asFlow())
            } catch (e: Exception) {
                Timber.e(e, "Failed to send message")
            }
        }
    }

    // T1 -> startFeature                Event Event endFeature
    // T2 ->              startFeature                           Event endFeature

    fun startFeature(value: String) {
        feature = value
    }

    fun endFeature() {
        feature = ""
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
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

        Log.d(LOGGY_TAG, "$sessionID State: ${channel.getState(false)} $message")
        logRepository.addMessage(loggyMessage.toByteArray())
        attemptToSendMessage()
    }

    private fun attemptToSendMessage() {
        if (channel.getState(true) != ConnectivityState.READY) {
            Log.e(LOGGY_TAG, "Connection Failed to Loggy Server")
            return
        } else {
            Log.e(LOGGY_TAG, "Server Connected. Try to send saved messages")
        }

        val bytes = logRepository.getMessageTop()
        if (bytes != null) {
            val message = Message.parseFrom(bytes)
            Log.d(LOGGY_TAG, "$message")
            messageChannel.offer(message)
            logRepository.removeTop() // Remove message once sent

            if (logRepository.hasMessages()) {
                // This means some messages are backed up and this attempts to resend recursively
                attemptToSendMessage()
            }
        } else {
            Log.d(LOGGY_TAG, "Empty Messages")
        }
    }
}
