package com.wire.kalium.logic.feature.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.persistence.client.SessionLocalDataSource
import com.wire.kalium.persistence.data_store.DataStoreStorage
import com.wire.kalium.persistence.util.SecurityUtil

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from AuthenticationScopeCommon
 */
actual class AuthenticationScope(
    loginNetworkContainer: LoginNetworkContainer,
    clientLabel: String,
    applicationContext: Context
) : AuthenticationScopeCommon(loginNetworkContainer, clientLabel) {

    private val dataStore: DataStore<Preferences> =
        preferencesDataStore(name = "data-store").getValue(applicationContext, String::javaClass)
    private val securityUtil: SecurityUtil get() = SecurityUtil()
    private val dataStoreStorage: DataStoreStorage = DataStoreStorage(dataStore, securityUtil)
    override val sessionLocalDataSource: SessionLocalDataSource get() = SessionLocalDataSource(dataStoreStorage)
}
