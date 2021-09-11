package loggy.sh

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import loggy.sh.utils.Hashids
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
            .isEqualTo(sessionID)
    }

    // TODO: 20/1/21 Missing test when #getOrInsertApplication throws an exception.
    // TODO: 20/1/21 Missing test when #getOrInsertDevice throws an exception.
    // TODO: 20/1/21 Missing test when #insertSession throws an exception.

    @Test
    fun `validate hash`() {

        val loggyContext = mock<LoggyContext>()

        val loggyApp =
            Application.newBuilder().setPackagename("loggy.sh.sample")
                .setId("bf0b5b86-62f0-4f87-9312-da3eeeceed0f")
                .build()
        val device = Device.newBuilder().setId("1076aa98-c4c4-4c28-8a33-16271e38e651").build()

        whenever(loggyContext.getApplication())
            .thenReturn(loggyApp)
        whenever(loggyContext.getDevice(loggyApp.id))
            .thenReturn(device)

        val hash = loggyContext.getDeviceHash(
            loggyContext.getApplication().id,
            loggyContext.getDevice().id
        )

        val (x, y) = hash.split("/")

        val deviceID = loggyContext.getDevice().id
        val applicationID = loggyContext.getApplication().id

        val deviceHashList = Hashids(deviceID, 6).decode(x).asList()
        val appHashList = Hashids(applicationID, 6).decode(y).asList()

        assertThat(appHashList)
            .isEqualTo(listOf(1, 2, 3))

        assertThat(deviceHashList)
            .isEqualTo(listOf(4, 4, 4))
    }
}
