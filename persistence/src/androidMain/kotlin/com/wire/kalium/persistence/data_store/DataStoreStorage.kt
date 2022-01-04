package com.wire.kalium.persistence.data_store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wire.kalium.persistence.util.JsonSerializer
import com.wire.kalium.persistence.util.SecurityUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class DataStoreStorage(
    val dataStore: DataStore<Preferences>,
    val security: SecurityUtil
) {
    fun getString(key: String): Flow<String> {
        return dataStore.data
            .map { preferences ->
                preferences[key.toDataStoreKey()].orEmpty()
            }
    }

    suspend fun setString(value: String, key: String) {
        dataStore.edit {
            it[key.toDataStoreKey()] = value
        }
    }

    inline fun <reified T> getSecuredData(key: String, securityKeyAlias: String) = dataStore.data
        .secureMap<T>(securityKeyAlias) { preferences ->
            preferences[key.toDataStoreKey()].orEmpty()
        }

    suspend inline fun <reified T> setSecuredData(value: T, key: String, securityKeyAlias: String) {
        dataStore.secureEdit(value, securityKeyAlias) { prefs, encryptedValue ->
            prefs[key.toDataStoreKey()] = encryptedValue
        }
    }

    suspend fun hasKey(key: Preferences.Key<*>): Boolean {
        var res: Boolean = false
        dataStore.edit {
            res = it.contains(key)
        }
        return res
    }


    suspend fun clearDataStore() {
        dataStore.edit {
            it.clear()
        }
    }

    inline fun <reified T> Flow<Preferences>.secureMap(
        securityKeyAlias: String,
        crossinline fetchValue: (value: Preferences) -> String
    ): Flow<T> {
        return map { preferences ->
            val decryptedValue = security.decryptData(
                securityKeyAlias,
                fetchValue(preferences).split(bytesToStringSeparator).map { it.toByte() }.toByteArray()
            )
            JsonSerializer().decodeFromString(decryptedValue)
        }
    }

    suspend inline fun <reified T> DataStore<Preferences>.secureEdit(
        value: T,
        securityKeyAlias: String,
        crossinline editStore: (MutablePreferences, String) -> Unit
    ) {
        edit {
            val encryptedValue = security.encryptData(securityKeyAlias, JsonSerializer().encodeToString(value))
            editStore.invoke(it, encryptedValue.joinToString(bytesToStringSeparator))
        }
    }

    fun String.toDataStoreKey() = stringPreferencesKey(this)

    companion object {
        const val bytesToStringSeparator = "|"
    }
}
