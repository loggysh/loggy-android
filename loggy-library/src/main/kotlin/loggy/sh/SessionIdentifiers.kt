package loggy.sh

data class SessionIdentifiers(
    val sessionId: Int, //id from server. all messages should be sent with this id
    val deviceId: String
)
