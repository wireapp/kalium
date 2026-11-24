/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.AuthTokenStorageImpl
import com.wire.kalium.persistence.client.NotificationTokenEntity
import com.wire.kalium.persistence.client.ProxyCredentialsEntity
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.client.TransactionalAuthTokenStorage
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl
import com.wire.kalium.util.InternalKaliumApi

internal class ProtectedAuthTokenStorage(private val file: ProtectedJvmPreferences) : TransactionalAuthTokenStorage {
    @InternalKaliumApi
    override suspend fun <T> inSessionTransaction(action: suspend () -> T): T = file.inSessionTransaction(action)

    override fun addOrReplace(authTokenEntity: AuthTokenEntity, proxyCredentialsEntity: ProxyCredentialsEntity?) = file.update {
        val storage = AuthTokenStorageImpl(it)
        if (proxyCredentialsEntity == null) storage.deleteToken(authTokenEntity.userId)
        storage.addOrReplace(authTokenEntity, proxyCredentialsEntity)
    }

    override fun updateToken(userId: UserIDEntity, accessToken: String, tokenType: String, refreshToken: String?): AuthTokenEntity =
        file.update { AuthTokenStorageImpl(it).updateToken(userId, accessToken, tokenType, refreshToken) }

    override fun getToken(userId: UserIDEntity): AuthTokenEntity? = file.read { AuthTokenStorageImpl(it).getToken(userId) }

    override fun deleteToken(userId: UserIDEntity) = file.update { AuthTokenStorageImpl(it).deleteToken(userId) }

    override fun proxyCredentials(userId: UserIDEntity): ProxyCredentialsEntity? =
        file.read { AuthTokenStorageImpl(it).proxyCredentials(userId) }
}

internal class ProtectedTokenStorage(private val file: ProtectedJvmPreferences) : TokenStorage {
    override fun saveToken(token: String, transport: String, applicationId: String) = file.update {
        TokenStorageImpl(it).saveToken(token, transport, applicationId)
    }

    override fun getToken(): NotificationTokenEntity? = file.read { TokenStorageImpl(it).getToken() }
}

internal class ProtectedPassphraseStorage(private val file: ProtectedJvmPreferences) : PassphraseStorage {
    override fun getPassphrase(key: String): String? = file.read { PassphraseStorageImpl(it).getPassphrase(key) }

    override fun setPassphrase(key: String, passphrase: String) = file.update {
        PassphraseStorageImpl(it).setPassphrase(key, passphrase)
    }

    override fun setPassphraseIfAbsent(key: String, passphrase: String): String = file.update {
        PassphraseStorageImpl(it).setPassphraseIfAbsent(key, passphrase)
    }

    override fun clearPassphrase(key: String) = file.update { PassphraseStorageImpl(it).clearPassphrase(key) }
}
