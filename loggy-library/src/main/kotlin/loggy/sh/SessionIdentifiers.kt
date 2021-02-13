package loggy.sh

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf

data class SessionIdentifiers(
    val sessionId: Int, //id from server. all messages should be sent with this id
    val deviceId: String
)

class SessionHandler(
    val dataStore: DataStore<Preferences>
) {

    val sessionKey = preferencesOf()

}
