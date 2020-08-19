package com.xyz.loggy

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.xyz.simple.*
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

    private val simple by lazy { SimpleServiceGrpcKt.SimpleServiceCoroutineStub(channel()) }
    private val applicationService by lazy {
        ApplicationServiceGrpcKt.ApplicationServiceCoroutineStub(
            channel()
        )
    }

    private val deviceService by lazy {
        DeviceServiceGrpcKt.DeviceServiceCoroutineStub(
            channel()
        )
    }
    private val instanceService by lazy {
        InstanceServiceGrpcKt.InstanceServiceCoroutineStub(
            channel()
        )
    }

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        val appID = ApplicationId.newBuilder()
            .setId(BuildConfig.APPLICATION_ID)
            .build()

        val deviceID = DeviceId.newBuilder()
            .setId(10)
            .build()

        val instanceId = InstanceId.newBuilder()
            .setAppid("Swiggy")
            .setId(1)
            .build()

//        GlobalScope.launch {
//            try {
//                val instance = Instance.newBuilder()
//                    .setAppid(BuildConfig.APPLICATION_ID)
//                    .setDeviceid(1)
//                    .build()
//
//                val ins = instanceService.insert(instance)
//                Timber.plant(TimberTree(instance, simple))
//                Timber.d("Loggy setup successful")
//            } catch (e: Exception) {
//                Timber.d("Loggy setup failed")
//                Timber.e(e)
//            }
//        }

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
