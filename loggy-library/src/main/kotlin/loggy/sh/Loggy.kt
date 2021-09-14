package loggy.sh

import android.app.Application
import android.content.Context
import android.util.Log
import android.webkit.URLUtil
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Timestamp
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.android.AndroidChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import loggy.sh.utils.HeaderClientInterceptor
import loggy.sh.utils.SessionPairSerializer
import loggy.sh.utils.SettingsSerializer
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import sh.loggy.internal.LoggyServiceGrpcKt
import sh.loggy.internal.LoggySettings
import sh.loggy.internal.Message
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.fixedRateTimer

enum class LoggyStatus(var description: String) {
    Initial("initial"),
    Setup("setup"),
    Connecting("connecting"),
    Connected("connected"),
    Disconnecting("disconnecting"),
    Failed("failed"),
    InvalidHost("Invalid Host"),
}

private interface LoggyInterface {
    fun setup(application: Application, hostUrl: String, clientID: String)
    fun log(priority: Int, tag: String?, message: String, t: Throwable?)
    fun interceptException(onException: (exception: Throwable) -> Boolean)
    suspend fun loggyDeviceUrl(): String
    fun close()
    fun status(): StateFlow<LoggyStatus>
}

val Context.sessionsDataStore: DataStore<LoggySettings.SessionPair> by dataStore(
    fileName = "sessions.pb",
    serializer = SessionPairSerializer
)

val Context.settingsDataStore: DataStore<LoggySettings.Settings> by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer
)

object Loggy : LoggyInterface {

    private val loggyImpl: LoggyImpl by lazy { LoggyImpl() }

    fun setup(application: Application, apiKey: String) {
        loggyImpl.setup(application, "https://loggy.sh", apiKey)
    }

    override fun setup(application: Application, hostUrl: String, apiKey: String) {
        loggyImpl.setup(application, hostUrl, apiKey)
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

    override fun close() {
        loggyImpl.close()
    }

    override fun status(): StateFlow<LoggyStatus> {
        return loggyImpl.status()
    }
}

private class LoggyImpl : LoggyInterface {

    private var retryAttempt = 1
    private var isInitialized = false
    private lateinit var url: URL
    private lateinit var channel: ManagedChannel

    private lateinit var loggyService: LoggyServiceGrpcKt.LoggyServiceCoroutineStub
    private val messageChannel = MutableSharedFlow<Message>(0, 10)
    private var messagingScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    private var internalSessionID: Int = -1
    private var sessionID: Int = -1
    private var feature: String? = null
    private var onInterceptException: (e: Throwable) -> Boolean = { false }

    private val noSessionMessages = LinkedBlockingQueue<Message>()
    private lateinit var loggyClient: LoggyClient
    private lateinit var logRepository: LogRepository
    private lateinit var loggyContext: LoggyContextForAndroid
    private var status = MutableStateFlow(LoggyStatus.Initial)

    override fun setup(application: Application, hostUrl: String, apiKey: String) {
        mainScope.async {
            //close any existing connection
            close()

            status.tryEmit(LoggyStatus.Setup)
            if (URLUtil.isNetworkUrl(hostUrl) || hostUrl.isNotEmpty()) {
                url = URL(hostUrl)
            } else {
                SupportLogs.error(
                    "Setup failed. Invalid Url $hostUrl. Check if it has protocol http"
                )
                status.tryEmit(LoggyStatus.InvalidHost.apply {
                    description = "$description $hostUrl"
                })
                cancel("Invalid Host Url $hostUrl")
            }

            installExceptionHandler()

            /**
             * Attempt connecting to server in 2s, 4s, 8s
             * Connection should be established within 15-30s
             * >30s server is down
             */
            try {
                status.tryEmit(LoggyStatus.Connecting)
                val port = 50111
                SupportLogs.info("Connecting to ${url.host}:${port}")
                channel = AndroidChannelBuilder.forAddress(url.host, port)
                    .context(application)
                    .usePlaintext()
                    .intercept(HeaderClientInterceptor(apiKey = apiKey))
                    .build()

                checkLoggyStateChangePeriodically()

                logRepository = LogRepository(application)
                loggyService = LoggyServiceGrpcKt.LoggyServiceCoroutineStub(channel)
                loggyClient = LoggyClient(application.sessionsDataStore, loggyService)

                loggyContext = LoggyContextForAndroid(application)

                scope.launch {
                    log(Log.INFO, LOGGY_TAG, "initializing...", null)
                    loggyClient.validateAndCreateDevice(loggyContext, apiKey)

                    val state = channel.getState(true)
                    SupportLogs.log("Initial State $state")
                    delay(1000) // delay for connection to be established.
                    val isSuccess = hasSuccessfulConnection()

                    SupportLogs.info("Setup data, context and client $isSuccess")
                    //increment only first time.
                    internalSessionID = loggyClient.newInternalSessionID()
                    updateSessionIDForNoSession(internalSessionID)

                    SupportLogs.info("Loggy State - ${channel.getState(false)}")

                    if (isSuccess) {
                        log(Log.INFO, LOGGY_TAG, "initialized", null)
                        connectionSuccessful()
                    } else {
                        connectionFailed(null)
                    }
                }
            } catch (e: Exception) {
                SupportLogs.error("Failed to setup loggy", e)
                connectionFailed(e)
            }
        }
    }

    override fun close() {
        if (this::loggyClient.isInitialized) {
            SupportLogs.log("Closing Loggy")
            status.tryEmit(LoggyStatus.Disconnecting)
            channel.shutdownNow()
            loggyClient.close()
            scope.cancel()
            scope = CoroutineScope(Dispatchers.IO)
        }
    }

    override fun status(): StateFlow<LoggyStatus> {
        return status
    }

    override suspend fun loggyDeviceUrl(): String {
        return "${url.host}/d/" + loggyContext.getDeviceHash(
            loggyContext.getApplicationID(), loggyContext.getDeviceID()
        )
    }

    override fun interceptException(onException: (exception: Throwable) -> Boolean) {
        onInterceptException = onException
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

        val sid = if (sessionID == -1) internalSessionID else sessionID

        // auto-increment
        val loggyMessage = Message
            .newBuilder()
            .setLevel(level)
            .setMsg(msg)
            .setSessionid(sid)
            .setTimestamp(timestamp)
            .build()

        if (loggyMessage.sessionid == -1) {
            holdMessageForNoSession(loggyMessage)
        } else {
            attemptToSendMessage(loggyMessage)
        }
    }

    fun identity(userID: String?, email: String?, userName: String?) {
        scope.launch {
            if (this@LoggyImpl::loggyContext.isInitialized) {
                loggyContext.saveIdentity(userID, email, userName)
            }
        }
    }

    private fun checkLoggyStateChangePeriodically() {
        scope.async {
            fixedRateTimer("status_timer", initialDelay = 0, period = 2000, action = {
                val currentStatus = status.value
                val channelState = channel.getState(false)
                if (channelState == ConnectivityState.READY
                    && currentStatus != LoggyStatus.Connected
                ) {
                    status.tryEmit(LoggyStatus.Connected)
                } else if (channelState == ConnectivityState.CONNECTING
                    && currentStatus != LoggyStatus.Connecting
                ) {
                    status.tryEmit(LoggyStatus.Connecting)
                } else if (channelState == ConnectivityState.SHUTDOWN
                    && currentStatus != LoggyStatus.Failed
                ) {
                    status.tryEmit(LoggyStatus.Failed)
                } else if (channelState == ConnectivityState.TRANSIENT_FAILURE
                    && currentStatus != LoggyStatus.Failed
                ) {
                    status.tryEmit(LoggyStatus.Failed)
                } else if (channelState == ConnectivityState.IDLE && currentStatus != LoggyStatus.Initial) {
                    status.tryEmit(LoggyStatus.Initial)
                }
            })
        }
    }

    private fun connectionSuccessful() {

        status.tryEmit(LoggyStatus.Connected)
        scope.launch {
            try {
                sessionID = loggyClient.createSession(loggyContext)
                loggyClient.mapSessionId(internalSessionID, sessionID)
                loggyClient.registerForLiveSession(sessionID)
                startListeningForMessages()
                isInitialized = true // create session was successful
                delay(1000)
                sendPendingMessagesIfAny()
            } catch (e: StatusException) {
                SupportLogs.error("Loggy failed with Status: ${e.status}")
                retryConnection()
            } catch (e: Exception) {
                SupportLogs.error("Unknown error", e)
                retryConnection()
            }
        }
    }

    private fun connectionFailed(t: Throwable?) {
        if (t == null) {
            status.tryEmit(LoggyStatus.Failed.apply {
                description = description + " " + channel.getState(false).name
            })
        } else {
            status.tryEmit(LoggyStatus.Failed.apply {
                description = description + " " + channel.getState(false).name + " " + Status
                    .fromThrowable(t).code.name
            })
        }
        retryConnection()
    }

    private fun retryConnection() {
        scope.async {
            retryAttempt++
            // 1 * 2 // 2 * 2 // 3 * 2
            delay(retryAttempt * 2000L)
            SupportLogs.info("Retrying Attempt $retryAttempt ${status.value}")
            if (hasSuccessfulConnection()) {
                connectionSuccessful()
                return@async
            } else if (status.value == LoggyStatus.Connecting) {
                //someone is retrying a connection
                return@async
            }
            channel.getState(true)
            delay(1000)
            if (!hasSuccessfulConnection()) {
                //creates recursion but is delayed with retry attempt.
                connectionFailed(null)
            }
        }
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

    private suspend fun startListeningForMessages() {
        messagingScope.launch {
            try {
                SupportLogs.log("Subscribed to listen to messages")
                loggyService.send(messageChannel)
            } catch (e: Exception) {
                SupportLogs.error("Failed to send message", e)
                connectionFailed(e)
            }
        }
    }

    private fun attemptToSendMessage(message: Message) {
        if (!isInitialized) {
            // check if server is setup
            logRepository.addMessage(message)
            return
        }

        SupportLogs.info("Session ID : $sessionID Internal : $internalSessionID")
        if (sessionID == -1) {
            messagingScope.launch {
                sessionID = loggyClient.getServerSessionID(message.sessionid)
            }
            logRepository.addMessage(message)
            return
        }

        if (!hasSuccessfulConnection()) {
            SupportLogs.error("Connection Failed to Loggy Server")
            logRepository.addMessage(message)
            return
        }

        SupportLogs.info("$sessionID State: ${channel.getState(false)} ${message.msg}")

        var msg = message
        if (message.sessionid != sessionID) {
            msg = message.toBuilder()
                .setSessionid(sessionID)
                .buildPartial()
        }

        messageChannel.tryEmit(msg)
        SupportLogs.error(
            "Server Connected. Try to send saved messages. Messages Left ${
                logRepository.messageCount()
            }"
        )

        //Check if any pending messages exist and attempt to send.
        sendPendingMessagesIfAny()
    }

    private fun sendPendingMessagesIfAny() {
        if (logRepository.hasMessages()) {
            // This means some messages are backed up and this attempts to resend recursively
            var parsedMessage: Message? = null
            try {
                parsedMessage = logRepository.getMessageTop()
            } catch (e: InvalidProtocolBufferException) {
                SupportLogs.error("Invalid message", e)
                return
            } catch (e: Exception) {
                SupportLogs.error(e.message, e)
                return
            }
            // remove any message from top.
            // Its possible for a message to get corrupted.`
            logRepository.removeTop()
            if (parsedMessage != null) {
                attemptToSendMessage(parsedMessage)
            }
        } else {
            SupportLogs.log("Empty Messages")
        }
    }

    private fun holdMessageForNoSession(message: Message) {
        noSessionMessages.put(message)
    }

    private fun updateSessionIDForNoSession(sessionID: Int) {
        if (sessionID == -1) {
            SupportLogs.error("Invalid Session ID", IllegalArgumentException("Invalid Session ID."))
            return
        }
        noSessionMessages.forEach {
            val m = it.toBuilder().setSessionid(sessionID).build()
            attemptToSendMessage(m)
        }
        noSessionMessages.clear()
    }

    private fun hasSuccessfulConnection(): Boolean {
        val state = channel.getState(false)
        SupportLogs.log("Check Connection : $state")
        if (state == ConnectivityState.READY || state == ConnectivityState.IDLE) {
            return true
        }
        return false
    }
}
