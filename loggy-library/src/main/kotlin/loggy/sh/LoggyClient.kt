package loggy.sh

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.firstOrNull
import sh.loggy.LoggyServiceGrpcKt
import sh.loggy.LoggySettings
import sh.loggy.Session
import sh.loggy.SessionId
import timber.log.Timber

class LoggyClient(
    private val sessionsDataStore: DataStore<LoggySettings.SessionPair>,
    private val loggyService: LoggyServiceGrpcKt.LoggyServiceCoroutineStub
) {

    private fun session(applicationID: String, deviceID: String): Session =
        Session.newBuilder()
            .setAppid(applicationID)
            .setDeviceid(deviceID)
            .build()

    suspend fun createSession(loggyContext: LoggyContext): Int {
        Timber.d("Register For Application")
        val loggyAppWithId = loggyService.getOrInsertApplication(loggyContext.getApplication())
        loggyContext.saveApplicationID(loggyAppWithId.id)

        Timber.d("Register For Device $loggyAppWithId")
        val deviceWithId = loggyService.getOrInsertDevice(loggyContext.getDevice(loggyAppWithId.id))

        Timber.d("Register For Session")
        val session = session(loggyAppWithId.id, deviceWithId.id) //TODO wont work offline
        val sessionWithId = loggyService.insertSession(session)

        return sessionWithId.id
    }

    suspend fun registerForLiveSession(sessionId: Int) {
        Timber.d("Register For Live Logs")
        val sessionWithId = SessionId.newBuilder()
            .setId(sessionId)
            .build()
        loggyService.registerSend(sessionWithId)
    }

    suspend fun newInternalSessionID(): Int {
        val newSessionID = (sessionsDataStore.data.firstOrNull()?.sessionCounter ?: 0) + 1

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
        Log.d(LOGGY_TAG, "Closing Client")
    }

}
