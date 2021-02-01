package loggy.sh.sample

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import loggy.sh.Loggy
import timber.log.Timber

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        Loggy.setup(
            this@MainApplication,
            "bf0b5b86-62f0-4f87-9312-da3eeeceed0f",
            "Lady Ada Lovelace"
        )
        Timber.plant(LoggyTree())
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
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() {
        Timber.d("Application Background")
    }
}
