package loggy.sh

import sh.loggy.LoggyServiceGrpcKt
import sh.loggy.Session
import sh.loggy.SessionId
import timber.log.Timber

class LoggyClient(
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

        Timber.d("Register For Device")
        val deviceWithId = loggyService.getOrInsertDevice(loggyContext.getDevice())

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

}
