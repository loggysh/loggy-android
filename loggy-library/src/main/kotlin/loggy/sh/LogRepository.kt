package loggy.sh

import android.app.Application
import android.util.Log
import com.squareup.tape2.ObjectQueue
import com.squareup.tape2.QueueFile
import okio.buffer
import okio.sink
import sh.loggy.internal.Message
import java.io.File
import java.io.IOException
import java.io.OutputStream

private class MessageConverter : ObjectQueue.Converter<Message> {
    override fun from(source: ByteArray): Message {
        return Message.parseFrom(source)
    }

    override fun toStream(value: Message, os: OutputStream) {
        try {
            val bytes = value.toByteArray()
            SupportLogs.log( "SIZE ${bytes.size}")
            if (bytes.isNotEmpty()) {
                os.sink().buffer().use { sink ->
                    sink.write(bytes)
                }
            }
        } catch (e: IOException) {
            SupportLogs.error( e.message, e)
        }
    }

}

class LogRepository(val application: Application) {

    /**
     * File 1 - loggy-nanotime.log - Session ID
     * File 2 - loggy-nanotime.log - Session ID
     */
    private val file by lazy { File("${application.filesDir.absolutePath}/logs.txt") }
    private val queueFile = QueueFile.Builder(file).build()
    private var useInMemory = true
    private val inMemoryQueue = ObjectQueue.createInMemory<Message>()
    private val objectFile =
        if (useInMemory)
            inMemoryQueue
        else
            ObjectQueue.create(queueFile, MessageConverter())

    fun addMessage(message: Message) {
        try {
            objectFile.add(message)
        } catch (e: Exception) {
            SupportLogs.error( e.message, e)
        }
    }

    fun getMessageTop(): Message? {
        try {
            if (objectFile.file()?.size() == 0 || objectFile.isEmpty || objectFile.size() < 0) {
                return null
            }
            return objectFile.peek()
        } catch (e: Exception) {
            SupportLogs.error( e.message, e)
        }
        return null
    }

    fun removeTop() {
        try {
            objectFile.remove()
        } catch (e: Exception) {
            SupportLogs.error( e.message, e)
        }
    }

    fun hasMessages(): Boolean {
        return !objectFile.isEmpty
    }

    fun messageCount(): Int {
        return objectFile.size()
    }

}