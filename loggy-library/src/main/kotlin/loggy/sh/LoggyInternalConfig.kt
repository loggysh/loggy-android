package loggy.sh

object LoggyInternalConfig {
    private var isLoggyDebuggingEnabled: Boolean = false

    @JvmStatic
    fun enableLoggyDebugging(enabled: Boolean): LoggyInternalConfig {
        isLoggyDebuggingEnabled = enabled
        return this
    }

    @JvmStatic
    fun isLoggyDebuggingEnabled(): Boolean {
        return isLoggyDebuggingEnabled
    }
}