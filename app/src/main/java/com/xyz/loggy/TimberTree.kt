package com.xyz.loggy

import android.util.Log
import com.google.protobuf.Timestamp
import com.xyz.simple.Instance
import com.xyz.simple.SimpleData
import com.xyz.simple.SimpleServiceGrpcKt
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber

class TimberTree(
    val instance: Instance,
    val simple: SimpleServiceGrpcKt.SimpleServiceCoroutineStub
) : Timber.DebugTree() {

    private val channel = BroadcastChannel<SimpleData>(Channel.CONFLATED)

    init {
        GlobalScope.launch {
            channel.asFlow()
                .collect { data ->
                    simple.simpleRPC(flow { data })
                }
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val request = SimpleData.newBuilder()
            .setId(instance.id)
            .setTimestamp(Timestamp.getDefaultInstance())
            .setMsg("$priority $tag $message")
            .build()

        channel.offer(request)
    }
}