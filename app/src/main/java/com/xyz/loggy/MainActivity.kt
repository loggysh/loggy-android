package com.xyz.loggy

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xyz.loggy.databinding.ActivityMainBinding
import com.xyz.simple.SimpleData
import com.xyz.simple.SimpleServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.net.URL
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private val simple by lazy { SimpleServiceGrpcKt.SimpleServiceCoroutineStub(channel()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        fixedRateTimer("hello", false, 0, 1000) {
            sendMessage()
        }
    }

    private fun sendMessage() {
        lifecycleScope.launch {
            val request = flow<SimpleData> {
                emit(SimpleData.newBuilder().setMsg("Hello Loggy!").build())
            }
            simple.simpleRPC(request)
                .collect {
                    Timber.d("$it")
                }
        }
    }

    private fun channel(): ManagedChannel {
        val url = URL("http://10.0.2.2:50111")
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
}