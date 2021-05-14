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
            hostUrl = "http://loggy.sh",
            clientID = "c7d4e293-ac2d-4d56-8fcf-4064e7238800"
        )
        Timber.plant(LoggyTree())

        Loggy.identity(
            userName = "Ada Lovelace"
        )

        Loggy.interceptException {
            Log.d("LoggyIntercept", "Failed")
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
