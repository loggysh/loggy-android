package loggy.sh

import sh.loggy.LoggyServiceGrpcKt
import sh.loggy.Session
import timber.log.Timber

class LoggyClient(
    private val loggyService: LoggyServiceGrpcKt.LoggyServiceCoroutineStub
) {

    private fun session(applicationID: String, deviceID: String): Session =
        Session.newBuilder()
            .setAppid(applicationID)
            .setDeviceid(deviceID)
            .build()

    suspend fun createSession(loggyContext: LoggyContext): SessionIdentifiers {
        Timber.d("Register For Application")
        val loggyAppWithId = loggyService.getOrInsertApplication(loggyContext.getApplication())

        Timber.d("Register New Device")
        val deviceWithId = loggyService.getOrInsertDevice(loggyContext.getDevice())

        Timber.d("Register For Session")
        val session = session(loggyAppWithId.id, deviceWithId.id) //TODO wont work offline
        val sessionWithId = loggyService.insertSession(session)

        Timber.d("${loggyContext.getDeviceHash(loggyAppWithId.id, deviceWithId.id)}")
        Timber.d("Register Send")
        loggyService.registerSend(sessionWithId) // TODO: 20/01/21 violates SRP
        return SessionIdentifiers(sessionWithId.id, deviceWithId.id)
    }


}
