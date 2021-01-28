package loggy.sh

import android.app.Application
import com.squareup.tape2.QueueFile
import java.io.File

class LogRepository(val application: Application) {

    private val file by lazy { File("${application.filesDir.absolutePath}/logs") }
    private val queueFile = QueueFile.Builder(file).zero(true).build()

    fun addMessage(bytes: ByteArray) {
        queueFile.add(bytes)
    }

    fun getMessageTop(): ByteArray? {
        return queueFile.peek()
    }

    fun messageCount(): Int {
        return queueFile.size()
    }

    fun removeTop() {
        queueFile.remove()
    }

}