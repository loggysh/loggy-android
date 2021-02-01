package loggy.sh

import android.app.Application
import com.squareup.tape2.ObjectQueue
import com.squareup.tape2.QueueFile
import sh.loggy.Message
import java.io.File
import java.io.OutputStream

class MessageConverter : ObjectQueue.Converter<Message> {
    override fun from(source: ByteArray): Message {
        return Message.parseFrom(source)
    }

    override fun toStream(value: Message, sink: OutputStream) {
        val array = value.toByteArray()
        sink.write(array)
    }

}

class LogRepository(val application: Application) {

    private val file by lazy { File("${application.filesDir.absolutePath}/logs.txt") }
    private val queueFile = QueueFile.Builder(file).zero(true).build()
    private val objectFile = ObjectQueue.create(queueFile, MessageConverter())

    fun addMessage(message: Message) {
        objectFile.add(message)
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