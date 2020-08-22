package loggy.sh.sample

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import loggy.sh.Loggy
import loggy.sh.LoggyConfig
import loggy.sh.loggy.sample.LoggyTree
import timber.log.Timber

class MainApplication : Application() {

    val scope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        /**
         * Avoid using hardware ids.
         * User FirebaseInstanceID
         * or and application level UUID
         */
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        scope.launch {
            Loggy.setup(
                this@MainApplication,
                LoggyConfig(
                    appID = BuildConfig.APPLICATION_ID,
                    uniqueDeviceID = androidId
                )
            )
            Timber.plant(LoggyTree())
        }
    }
}

class ForegroundBackgroundObserver : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun create() {
        Timber.d("Application Created")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun start() {
        Timber.d("Application Foreground")
        Loggy.startSession()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() {
        Timber.d("Application Background")
        Loggy.endSession()
    }
}
