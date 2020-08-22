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
                    appID = "4d975364-0181-43b8-bbc4-567ff50cc85e",
                    uniqueDeviceID = "5004b715-442d-4bb8-8078-3bd8bab1cccc"
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
        Loggy.startSession()
        Timber.d("Application Foreground")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() {
        Timber.d("Application Background")
        Loggy.endSession()
    }
}
