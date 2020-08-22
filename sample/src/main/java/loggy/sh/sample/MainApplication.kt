package loggy.sh.sample

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import loggy.sh.Loggy
import loggy.sh.LoggyConfig
import loggy.sh.loggy.sample.LoggyTree
import timber.log.Timber

class MainApplication : Application() {

    private val scope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        scope.launch {
            Loggy.setup(
                this@MainApplication,
                LoggyConfig(
                    appID = "e7315338-f2fc-4d17-8a53-d1d8f85b93db",
                    uniqueDeviceID = "5004b715-442d-4bb8-8078-3bd8bab190aa"
                )
            )
            Timber.plant(LoggyTree())
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundBackgroundObserver())
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
