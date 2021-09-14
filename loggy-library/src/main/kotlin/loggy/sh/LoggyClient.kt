package loggy.sh

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.firstOrNull
import sh.loggy.internal.LoggyServiceGrpcKt
import sh.loggy.internal.LoggySettings
import sh.loggy.internal.Session
import sh.loggy.internal.SessionId

class LoggyClient(
    private val sessionsDataStore: DataStore<LoggySettings.SessionPair>,
    private val loggyService: LoggyServiceGrpcKt.LoggyServiceCoroutineStub
) {

    private fun session(applicationID: String, deviceID: String): Session =
        Session.newBuilder()
            .setAppid(applicationID)
            .setDeviceid(deviceID)
            .build()

    suspend fun validateAndCreateDevice(loggyContext: LoggyContext, apiKey: String) {
        val savedApiKey = loggyContext.getApiKey()
        val hasApiKeyChanged = apiKey != savedApiKey

        if (hasApiKeyChanged) {
            loggyContext.generateAndSaveDeviceID()
        } else if (loggyContext.getDeviceID().isNullOrEmpty()) {
            loggyContext.generateAndSaveDeviceID()
        }

        loggyContext.saveApiKey(apiKey)
    }

    suspend fun createSession(loggyContext: LoggyContext): Int {
        SupportLogs.log("Register For Application")

        val loggyAppWithId = loggyService.getOrInsertApplication(loggyContext.getApplication())
        loggyContext.saveApplicationID(loggyAppWithId.id)

        SupportLogs.log("Register For Device with app id = ${loggyAppWithId.id}")
        val deviceWithId = loggyService
            .getOrInsertDevice(
                loggyContext.getDevice(loggyAppWithId.id)
            )

        SupportLogs.log("Register For Session with device id = ${deviceWithId.id}")
        val session = session(loggyAppWithId.id, deviceWithId.id) //TODO wont work offline
        val sessionWithId = loggyService.insertSession(session)

        return sessionWithId.id
    }

    suspend fun registerForLiveSession(sessionId: Int) {
        SupportLogs.log("Register For Live Logs")
        val sessionWithId = SessionId.newBuilder()
            .setId(sessionId)
            .build()
        loggyService.registerSend(sessionWithId)
    }

    suspend fun newInternalSessionID(): Int {
        var newSessionID = sessionsDataStore.data.firstOrNull()?.sessionCounter

        if (newSessionID == 0 || newSessionID == null) {
            newSessionID = Int.MAX_VALUE
        }
        newSessionID -= 1

        //add to store
        sessionsDataStore.updateData { sessions ->
            sessions.toBuilder().setSessionCounter(newSessionID)
                .putSessions(newSessionID, LoggySettings.SessionIdentifier.getDefaultInstance())
                .build()
        }
        return newSessionID
    }

    suspend fun mapSessionId(currentSession: Int, sid: Int) {
        sessionsDataStore.updateData { sessions ->
            val map = sessions.sessionsMap
            var identifier = map[currentSession]
            if (identifier != null && identifier.id == 0) {
                identifier = identifier.toBuilder().setId(sid).build()
            } else {
                identifier =
                    LoggySettings.SessionIdentifier.getDefaultInstance().toBuilder().setId(sid)
                        .build()
            }
            sessions.toBuilder().setSessionCounter(sessions.sessionCounter + 1)
                .putSessions(currentSession, identifier)
                .build()
        }
    }

    suspend fun getServerSessionID(currentSession: Int): Int {
        return sessionsDataStore.data.firstOrNull()?.sessionsMap?.get(currentSession)?.id
            ?: 0
    }

    fun close() {
        SupportLogs.log("Closing Client")
    }

}
