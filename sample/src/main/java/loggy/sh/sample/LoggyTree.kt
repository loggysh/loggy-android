package loggy.sh.sample

import loggy.sh.Loggy
import timber.log.Timber

class LoggyTree(private val loggy: Loggy) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        loggy.log(priority, tag, message, t)
    }
}