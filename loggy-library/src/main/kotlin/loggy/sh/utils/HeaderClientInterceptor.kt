package loggy.sh.utils

import io.grpc.*

class HeaderClientInterceptor(val userId: String) : ClientInterceptor {

    private val USER_ID_HEADER_KEY =
        Metadata.Key.of("user_id", Metadata.ASCII_STRING_MARSHALLER)

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        method: MethodDescriptor<ReqT, RespT>?,
        callOptions: CallOptions?,
        next: Channel?
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next!!.newCall(
                method,
                callOptions
            )
        ) {
            override fun start(
                responseListener: Listener<RespT>?,
                headers: Metadata
            ) {
                /* put custom header */
                headers.put(USER_ID_HEADER_KEY, userId)
                super.start(object :
                    ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                        responseListener
                    ) {
                    override fun onHeaders(headers: Metadata) {
                        /**
                         * if you don't need receive header from server,
                         * you can use [io.grpc.stub.MetadataUtils.attachHeaders]
                         * directly to send header
                         */
                        super.onHeaders(headers)
                    }
                }, headers)
            }
        }
    }

}