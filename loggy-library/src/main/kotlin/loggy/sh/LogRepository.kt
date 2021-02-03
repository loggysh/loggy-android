package loggy.sh

import android.app.Application
import android.util.Log
import com.squareup.tape2.ObjectQueue
import com.squareup.tape2.QueueFile
import okio.buffer
import okio.sink
import sh.loggy.Message
import java.io.File
import java.io.IOException
import java.io.OutputStream

class MessageConverter : ObjectQueue.Converter<Message> {
    override fun from(source: ByteArray): Message {
        return Message.parseFrom(source)
    }

    override fun toStream(value: Message, os: OutputStream) {
        try {
            val bytes = value.toByteArray()
            if (bytes.isNotEmpty()) {
                os.sink().buffer().use { sink ->
                    sink.write(bytes)
                }
            }
        } catch (e: IOException) {
            Log.e(LOGGY_TAG, e.message, e)
        }
    }

}

class LogRepository(val application: Application) {

    private val file by lazy { File("${application.filesDir.absolutePath}/logs.txt") }
    private val queueFile = QueueFile.Builder(file).zero(true).build()
    private val objectFile = ObjectQueue.create(queueFile, MessageConverter())

    fun addMessage(message: Message) {
        try {
            objectFile.add(message)
        } catch (e: Exception) {
            Log.e(LOGGY_TAG, e.message, e)
        }
    }

    fun getMessageTop(): Message? {
        return objectFile.peek()
    }

    fun removeTop() {
        objectFile.remove()
    }

    fun hasMessages(): Boolean {
        return !objectFile.isEmpty
    }

    fun messageCount(): Int {
        return objectFile.size()
    }

}