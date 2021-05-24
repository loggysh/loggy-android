package loggy.sh

import android.app.Application
import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Timestamp
import io.grpc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sh.loggy.LoggyServiceGrpcKt
import sh.loggy.Message
import timber.log.Timber
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.LinkedBlockingQueue

const val LOGGY_TAG = "Loggy"

private interface LoggyInterface {
    fun setup(application: Application, hostUrl: String, clientID: String)
    fun log(priority: Int, tag: String?, message: String, t: Throwable?)
    fun interceptException(onException: (exception: Throwable) -> Boolean)
    suspend fun loggyDeviceUrl(): String
}

object Loggy : LoggyInterface {

    private val loggyImpl: LoggyImpl by lazy { LoggyImpl() }

    override fun setup(application: Application, hostUrl: String, clientID: String) {
        loggyImpl.setup(application, hostUrl, clientID)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        loggyImpl.log(priority, tag, message, t)
    }

    override suspend fun loggyDeviceUrl(): String {
        return loggyImpl.loggyDeviceUrl()
    }

    /**
     * Cannot overide from interface. Since Interface defaults overriding is not allowed.
     */
    fun identity(userID: String? = "", email: String? = "", userName: String? = "") {
        loggyImpl.identity(userID, email, userName)
    }

    override fun interceptException(onException: (exception: Throwable) -> Boolean) {
        loggyImpl.interceptException(onException)
    }
}

private class LoggyImpl : LoggyInterface {
//    private val homeUrl = "loggy.sh"
//    private val url = URL(BuildConfig.loggyUrl)
//    private val port = if (url.port == -1) url.defaultPort else url.port

    private var isInitialized = false
    private lateinit var url: URL
    private lateinit var channel: ManagedChannel

    private lateinit var loggyService: LoggyServiceGrpcKt.LoggyServiceCoroutineStub
    private val messageChannel = BroadcastChannel<Message>(Channel.BUFFERED)

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var sessionID: Int = -1
    private var feature: String? = null
    private var onInterceptException: (e: Throwable) -> Boolean = { false }

    private val noSessionMessages = LinkedBlockingQueue<Message>()
    private lateinit var loggyClient: LoggyClient
    private lateinit var logRepository: LogRepository
    private lateinit var loggyContext: LoggyContextForAndroid

    override fun setup(application: Application, hostUrl: String, clientID: String) {
        this.url = URL(hostUrl)
        val port = 50111
        installExceptionHandler()

        try {
            Log.i(LOGGY_TAG, "Connecting to ${url.host}:${port}")
            this.channel =
                ManagedChannelBuilder.forAddress(url.host, port)
                    .apply {
                        if (url.protocol == "https") {
                            useTransportSecurity()
                        } else {
                            usePlaintext()
                        }
                    }
                    .intercept(object : ClientInterceptor {
                        override fun <ReqT : Any?, RespT : Any?> interceptCall(
                            method: MethodDescriptor<ReqT, RespT>?,
                            callOptions: CallOptions?,
                            next: io.grpc.Channel?
                        ): ClientCall<ReqT, RespT> {
                            Log.d(LOGGY_TAG, "${java.lang.Exception().printStackTrace()}")
                            return next!!.newCall(method, callOptions)
                        }

                    })
                    .build()
            logRepository = LogRepository(application)
            loggyService = LoggyServiceGrpcKt.LoggyServiceCoroutineStub(channel)
            loggyClient = LoggyClient(application, loggyService)

            loggyContext = LoggyContextForAndroid(application, clientID)

            Log.i(LOGGY_TAG, "Setup data, context and client")
            scope.launch {

                //increment only first time.
                sessionID = loggyClient.newInternalSessionID()

                updateSessionIDForNoSession(sessionID)

                var isSuccess = true
                if (channel.getState(true) != ConnectivityState.READY) {
                    isSuccess = false
                }

                if (isSuccess) {
                    val serverSessionID = loggyClient.createSession(loggyContext).runCatching {
                        this
                    }

                    if (serverSessionID.isSuccess) {
                        loggyClient.mapSessionId(sessionID, serverSessionID.getOrDefault(sessionID))
                        loggyClient.registerForLiveSession(serverSessionID.getOrDefault(sessionID))
                        startListeningForMessages()
                        isInitialized = true // create session was successful
                        sendPendingMessagesIfAny()
                    } else {
                        Log.e(LOGGY_TAG, LOGGY_TAG, serverSessionID.exceptionOrNull())
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup loggy")
        }
    }

    override suspend fun loggyDeviceUrl(): String {
        return "${url.host}/d/" + loggyContext.getDeviceHash(
            loggyContext.getApplicationID(), loggyContext.getDeviceID()
        )
    }

    private fun installExceptionHandler() { // TODO Platform dependent
        val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            log(100, "Thread: ${thread.name}", e.message ?: "Unknown Message", e)

            if (!onInterceptException(e)) {
                defaultUncaughtExceptionHandler?.uncaughtException(thread, e) // Thanks Ragunath.
            }
        }
    }

    private fun startListeningForMessages() {
        scope.launch {
            Log.e(LOGGY_TAG, "Channel ${Thread.currentThread().name}")

            try {
                loggyService.send(messageChannel
                    .asFlow()
                    .map { item ->
                        //set server session ID
                        val sid = loggyClient.getServerSessionID(item.sessionid)
                        Log.i(LOGGY_TAG, "$sid State: ${channel.getState(false)} ${item.msg}")
                        item.toBuilder()
                            .setSessionid(sid)
                            .build()
                    })
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

        if (loggyMessage.sessionid == -1) {
            holdMessageForNoSession(loggyMessage)
        } else {
            attemptToSendMessage(loggyMessage)
        }
    }

    override fun interceptException(onException: (exception: Throwable) -> Boolean) {
        onInterceptException = onException
    }

    fun identity(userID: String?, email: String?, userName: String?) {
        scope.launch {
            loggyContext.saveIdentity(userID, email, userName)
        }
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

        messageChannel.trySend(message).isSuccess
        Log.e(
            LOGGY_TAG,
            "Server Connected. Try to send saved messages. Messages Left ${
                logRepository
                    .messageCount()
            }"
        )
        sendPendingMessagesIfAny()
    }

    private fun sendPendingMessagesIfAny() {
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
            // Its possible for a message to get corrupted.`
            logRepository.removeTop()
            if (parsedMessage != null) {
                attemptToSendMessage(parsedMessage)
            }
        } else {
            Log.d(LOGGY_TAG, "Empty Messages")
        }
    }

    fun holdMessageForNoSession(message: Message) {
        noSessionMessages.put(message)
    }

    fun updateSessionIDForNoSession(sessionID: Int) {
        if (sessionID == -1) {
            Timber.e(IllegalArgumentException("Invalid Session ID."), "Invalid Session ID")
            return
        }
        noSessionMessages.forEach {
            val m = it.toBuilder().setSessionid(sessionID).build()
            attemptToSendMessage(m)
        }
        noSessionMessages.clear()
    }
}
