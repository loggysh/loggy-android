package loggy.sh

import android.util.Log

const val LOGGY_TAG = "Loggy"

class SupportLogs {

    companion object {

        private val enabled: Boolean = false

        fun log(message: String) {
            if (enabled) {
                Log.d(LOGGY_TAG, message)
            }
        }

        fun error(message: String? = "", t: Throwable? = null) {
            if (enabled) {
                Log.e(LOGGY_TAG, message, t)
            }
        }

        fun info(message: String) {
            if (enabled) {
                Log.i(LOGGY_TAG, message)
            }
        }
    }

}