package loggy.sh.sample

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import loggy.sh.Loggy
import loggy.sh.LoggyTree
import timber.log.Timber

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        Timber.plant(LoggyTree())

        setup()
        ProcessLifecycleOwner.get().lifecycle.addObserver(ForegroundBackgroundObserver())
    }

    fun setup() {
        Loggy.setup(
            this@MainApplication,
            apiKey = "932c3201886b455f877ff66076b4e24f",
            hostUrl = "https://10.0.2.2"
        )

        Loggy.identity(
            userName = "Ada Lovelace"
        )

//        Loggy.interceptException {
//            Log.d("LoggyIntercept", "Failed", it)
//            true
//        }
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
