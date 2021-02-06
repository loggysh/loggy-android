package loggy.sh.utils

import android.util.Log
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.protobuf.InvalidProtocolBufferException
import loggy.sh.LOGGY_TAG
import sh.loggy.LoggySettings.SessionPair
import sh.loggy.LoggySettings.SessionIdentifier
import sh.loggy.LoggySettings.Settings
import java.io.InputStream
import java.io.OutputStream

object SessionPairSerializer : Serializer<SessionPair> {
    override val defaultValue: SessionPair
        get() = SessionPair.getDefaultInstance()

    override fun readFrom(input: InputStream): SessionPair {
        try {
            return SessionPair.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(LOGGY_TAG, "Cannot read proto.", exception)
        }
        return defaultValue
    }

    override fun writeTo(t: SessionPair, output: OutputStream) =
        t.writeTo(output)
}

object SessionIdentifierSerializer : Serializer<SessionIdentifier> {
    override val defaultValue: SessionIdentifier
        get() = SessionIdentifier.getDefaultInstance()

    override fun readFrom(input: InputStream): SessionIdentifier {
        try {
            return SessionIdentifier.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(LOGGY_TAG, "Cannot read proto.", exception)
        }
        return defaultValue
    }

    override fun writeTo(t: SessionIdentifier, output: OutputStream) = t.writeTo(output)
}


object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings
        get() = Settings.getDefaultInstance()

    override fun readFrom(input: InputStream): Settings {
        try {
            return Settings.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(LOGGY_TAG, "Cannot read proto.", exception)
        }
        return defaultValue
    }

    override fun writeTo(t: Settings, output: OutputStream) = t.writeTo(output)
}