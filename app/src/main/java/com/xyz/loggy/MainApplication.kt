package com.xyz.loggy

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URL


class MainApplication : Application() {

    private fun channel(): ManagedChannel {
        val url = URL(BuildConfig.loggyUrl)
        val port = if (url.port == -1) url.defaultPort else url.port

        Timber.i("Connecting to ${url.host}:$port")

        val builder = ManagedChannelBuilder.forAddress(url.host, port)
        if (url.protocol == "https") {
            builder.useTransportSecurity()
        } else {
            builder.usePlaintext()
        }

        return builder.executor(Dispatchers.Default.asExecutor()).build()
    }

    private val loggy by lazy { LoggyServiceGrpcKt.LoggyServiceCoroutineStub(channel()) }

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        val instanceID = InstanceId.newBuilder()
            .setAppid(BuildConfig.APPLICATION_ID)
            .setId(1)
            .build()

        GlobalScope.launch {
            try {
                Timber.plant(TimberTree(instanceID, loggy))
                Timber.d("Loggy setup successful")
            } catch (e: Exception) {
                Timber.d("Loggy setup failed")
                Timber.e(e)
            }
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
