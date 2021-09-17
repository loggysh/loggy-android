package loggy.sh

import android.util.Log

const val LOGGY_TAG = "Loggy"

class SupportLogs {

    companion object {

        fun log(message: String) {
            if (LoggyInternalConfig.isLoggyDebuggingEnabled()) {
                Log.d(LOGGY_TAG, message)
            }
        }

        fun error(message: String? = "", t: Throwable? = null) {
            if (LoggyInternalConfig.isLoggyDebuggingEnabled()) {
                Log.e(LOGGY_TAG, message, t)
            }
        }

        fun info(message: String) {
            if (LoggyInternalConfig.isLoggyDebuggingEnabled()) {
                Log.i(LOGGY_TAG, message)
            }
        }
    }

}