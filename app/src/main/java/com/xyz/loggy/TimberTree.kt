package com.xyz.loggy

import com.google.protobuf.Timestamp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class TimberTree(
    private val instance: InstanceId,
    private val loggy: LoggyServiceGrpcKt.LoggyServiceCoroutineStub
) : Timber.DebugTree() {

    private val channel = BroadcastChannel<LoggyMessage>(Channel.CONFLATED)

    init {
        GlobalScope.launch {
            loggy.loggyServer(channel.asFlow())
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val request = LoggyMessage.newBuilder()
            .setId(instance.id)
            .setTimestamp(Timestamp.getDefaultInstance())
            .setMsg("$priority $tag $message")
            .build()

        channel.offer(request)
    }
}