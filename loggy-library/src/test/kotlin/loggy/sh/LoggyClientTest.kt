package loggy.sh

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import sh.loggy.Application
import sh.loggy.Device
import sh.loggy.LoggyServiceGrpcKt
import sh.loggy.SessionId

class LoggyClientTest {
    @ExperimentalCoroutinesApi
    @Test
    fun `it returns valid session identifiers if all RPCs are successful`() = runBlockingTest {
        // given
        val appID = "sh.loggy.sample"
        val deviceID = "my-device-id"
        val sessionID = 5

        val loggyService = mock<LoggyServiceGrpcKt.LoggyServiceCoroutineStub>()
        val loggyClient = LoggyClient(loggyService)

        val loggyApp = Application.newBuilder().build()
        val device = Device.newBuilder().build()

        val loggyContext = mock<LoggyContext>()
        whenever(loggyContext.getApplication())
            .thenReturn(loggyApp)
        whenever(loggyContext.getDevice())
            .thenReturn(device)

        whenever(loggyService.getOrInsertApplication(loggyApp))
            .thenReturn(loggyApp.toBuilder().setId(appID).build())
        whenever(loggyService.getOrInsertDevice(device))
            .thenReturn(device.toBuilder().setId(deviceID).build())
        whenever(loggyService.insertSession(any()))
            .thenReturn(SessionId.newBuilder().setId(sessionID).build())

        // when
        val session = loggyClient.createSession(loggyContext)

        // then
        assertThat(session)
            .isEqualTo(SessionIdentifiers(sessionID, deviceID))
    }

    // TODO: 20/1/21 Missing test when #getOrInsertApplication throws an exception.
    // TODO: 20/1/21 Missing test when #getOrInsertDevice throws an exception.
    // TODO: 20/1/21 Missing test when #insertSession throws an exception.
}
