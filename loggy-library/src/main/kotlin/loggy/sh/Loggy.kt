package loggy.sh

import android.app.Application
import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
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

const val LOGGY_TAG = "Loggy"

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

    private var isInitialized = false

    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(url.host, port)
        .apply {
            Log.i(LOGGY_TAG, "Connecting to ${url.host}:$port")
            if (url.protocol == "https") {
                useTransportSecurity()
            } else {
                usePlaintext()
            }
        }.build()

    private val loggyService by lazy { LoggyServiceGrpcKt.LoggyServiceCoroutineStub(channel) }

    private val messageChannel = BroadcastChannel<Message>(Channel.BUFFERED)

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var sessionID: Int = 0
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
                isInitialized = true // create session was successful
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

    /**
     * S - Start Application - End Process = 0
     * App Start - Create QueueFile -- M1 M2 M3 M4 App Killed-- 0
     * App online - create new session - send data
     * App Start - Create QueueFile-- M5 M6 M7 App Killed-- 1
     * App online - create new session - send data
     *
     * 1. Increment on Device and send messages.
     * 2. Associate Session ID with Device ID
     *
     * S1 M1 M2 M3 M4
     *                S2 M5 M6 M7
     *
     * D1 -  1076aa98-c4c4-4c28-8a33-16271e38e651
     *
     * Select * from sessions where device_id=1076aa98-c4c4-4c28-8a33-16271e38e651
     * 1, 2, 3, 4
     * Select * from message where device_id=1076aa98-c4c4-4c28-8a33-16271e38e651 AND session_id=1
     *
     * rpc RegisterSend (SessionId) returns (google.protobuf.Empty) {}
     * rpc RegisterReceive (SessionId) returns (ReceiverId) {}
     *
     * App Session - 1 - Server Session ID - undefined - then - insertSession - 101
     * App Session - 2 - Server Session ID - undefined - insertSession - 102
     * App Session - 3 - Server Session ID - undefined - insertSession - 103
     */

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

        // auto-increment
        val loggyMessage = Message
            .newBuilder()
            .setLevel(level)
            .setMsg(msg)
            .setSessionid(sessionID)
            .setTimestamp(timestamp)
            .build()

        Log.d(LOGGY_TAG, "$sessionID State: ${channel.getState(false)} $message")
        attemptToSendMessage(loggyMessage)
    }

    private fun attemptToSendMessage(message: Message) {
        if (!isInitialized) {
            // check if server is setup
            logRepository.addMessage(message)
            return
        }

        if (channel.getState(true) != ConnectivityState.READY) {
            Log.e(LOGGY_TAG, "Connection Failed to Loggy Server")
            logRepository.addMessage(message)
            return
        }

        messageChannel.offer(message)
        Log.e(
            LOGGY_TAG,
            "Server Connected. Try to send saved messages. Messages Left ${logRepository
                .messageCount()}"
        )

        if (logRepository.hasMessages()) {
            // This means some messages are backed up and this attempts to resend recursively
            var parsedMessage: Message? = null
            try {
                parsedMessage = logRepository.getMessageTop()
            } catch (e: InvalidProtocolBufferException) {
                Log.e(LOGGY_TAG, "Invalid message", e)
            } catch (e: Exception) {
                Log.e(LOGGY_TAG, e.message, e)
            }
            // remove any message from top.
            // Its possible for a message to get corrupted.
            logRepository.removeTop()
            if (parsedMessage != null) {
                attemptToSendMessage(parsedMessage)
            }
        } else {
            Log.d(LOGGY_TAG, "Empty Messages")
        }
    }
}
