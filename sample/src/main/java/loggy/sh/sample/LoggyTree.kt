package loggy.sh.sample

import loggy.sh.Loggy
import timber.log.Timber

class LoggyTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Loggy.log(priority, tag, message, t)
    }
}