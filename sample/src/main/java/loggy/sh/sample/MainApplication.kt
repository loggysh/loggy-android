package loggy.sh.sample

import android.app.Application
import android.util.Log
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
            hostUrl = "http://staging.loggy.sh",
            clientID = "d4d7f2b0-7833-4d91-bfa2-4cdfaacb68df"
        )
        Timber.plant(LoggyTree())

        Loggy.identity(
            userName = "Ada Lovelace"
        )

        Loggy.interceptException {
            Log.d("LoggyIntercept", "Failed", it)
            true
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
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() {
        Timber.d("Application Background")
    }
}
