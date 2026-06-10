/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.daokaliumdb

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.GlobalSecretsQueries
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

data class GlobalSecretsSnapshot(
    val authSessions: Map<UserIDEntity, GlobalAuthSessionEntity> = emptyMap(),
    val proxyCredentials: Map<UserIDEntity, GlobalProxyCredentialsEntity> = emptyMap(),
    val pushRegistration: GlobalPushRegistrationEntity? = null,
    val dbSecrets: Map<String, GlobalDbSecretEntity> = emptyMap()
) {
    fun copySecrets(): GlobalSecretsSnapshot =
        copy(dbSecrets = dbSecrets.mapValues { (_, secret) -> secret.copySecret() })
}

data class GlobalAuthSessionEntity(
    val userId: UserIDEntity,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val cookieLabel: String?,
    val updatedAt: Long
)

data class GlobalProxyCredentialsEntity(
    val userId: UserIDEntity,
    val username: String,
    val password: String,
    val updatedAt: Long
)

data class GlobalPushRegistrationEntity(
    val token: String,
    val transport: String,
    val applicationId: String,
    val updatedAt: Long
)

data class GlobalDbSecretEntity(
    val alias: String,
    val secret: ByteArray,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GlobalDbSecretEntity

        if (version != other.version) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (alias != other.alias) return false
        if (!secret.contentEquals(other.secret)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + secret.contentHashCode()
        return result
    }
}

interface GlobalSecretsDAO {
    suspend fun startupSecrets(): GlobalSecretsSnapshot

    suspend fun upsertAuthSession(authSession: GlobalAuthSessionEntity)
    suspend fun authSession(userId: UserIDEntity): GlobalAuthSessionEntity?
    suspend fun deleteAuthSession(userId: UserIDEntity)

    suspend fun upsertProxyCredentials(proxyCredentials: GlobalProxyCredentialsEntity)
    suspend fun proxyCredentials(userId: UserIDEntity): GlobalProxyCredentialsEntity?
    suspend fun deleteProxyCredentials(userId: UserIDEntity)

    suspend fun upsertPushRegistration(pushRegistration: GlobalPushRegistrationEntity)
    suspend fun pushRegistration(): GlobalPushRegistrationEntity?
    suspend fun deletePushRegistration()

    suspend fun upsertDbSecret(dbSecret: GlobalDbSecretEntity)
    suspend fun dbSecret(alias: String): GlobalDbSecretEntity?
    suspend fun deleteDbSecret(alias: String)
}

internal class DatabaseBackedGlobalSecretsCache(
    private val globalSecretsDAO: GlobalSecretsDAO
) {
    private val mutex = Mutex()
    private var snapshot: GlobalSecretsSnapshot? = null

    suspend fun authSession(userId: UserIDEntity): GlobalAuthSessionEntity? =
        startupSecrets().authSessions[userId]

    suspend fun proxyCredentials(userId: UserIDEntity): GlobalProxyCredentialsEntity? =
        startupSecrets().proxyCredentials[userId]

    suspend fun pushRegistration(): GlobalPushRegistrationEntity? =
        startupSecrets().pushRegistration

    suspend fun dbSecret(alias: String): GlobalDbSecretEntity? =
        startupSecrets().dbSecrets[alias]?.copySecret()

    suspend fun upsertAuthSession(authSession: GlobalAuthSessionEntity) {
        globalSecretsDAO.upsertAuthSession(authSession)
        updateLoadedSnapshot {
            copy(authSessions = authSessions + (authSession.userId to authSession))
        }
    }

    suspend fun deleteAuthSession(userId: UserIDEntity) {
        globalSecretsDAO.deleteAuthSession(userId)
        updateLoadedSnapshot {
            copy(authSessions = authSessions - userId)
        }
    }

    suspend fun upsertProxyCredentials(proxyCredentials: GlobalProxyCredentialsEntity) {
        globalSecretsDAO.upsertProxyCredentials(proxyCredentials)
        updateLoadedSnapshot {
            copy(proxyCredentials = this.proxyCredentials + (proxyCredentials.userId to proxyCredentials))
        }
    }

    suspend fun deleteProxyCredentials(userId: UserIDEntity) {
        globalSecretsDAO.deleteProxyCredentials(userId)
        updateLoadedSnapshot {
            copy(proxyCredentials = proxyCredentials - userId)
        }
    }

    suspend fun upsertPushRegistration(pushRegistration: GlobalPushRegistrationEntity) {
        globalSecretsDAO.upsertPushRegistration(pushRegistration)
        updateLoadedSnapshot {
            copy(pushRegistration = pushRegistration)
        }
    }

    suspend fun upsertDbSecret(dbSecret: GlobalDbSecretEntity) {
        globalSecretsDAO.upsertDbSecret(dbSecret)
        updateLoadedSnapshot {
            copy(dbSecrets = dbSecrets + (dbSecret.alias to dbSecret.copySecret()))
        }
    }

    suspend fun deleteDbSecret(alias: String) {
        globalSecretsDAO.deleteDbSecret(alias)
        updateLoadedSnapshot {
            copy(dbSecrets = dbSecrets - alias)
        }
    }

    private suspend fun startupSecrets(): GlobalSecretsSnapshot =
        snapshot ?: mutex.withLock {
            snapshot ?: globalSecretsDAO.startupSecrets().copySecrets().also { snapshot = it }
        }

    private suspend fun updateLoadedSnapshot(update: GlobalSecretsSnapshot.() -> GlobalSecretsSnapshot) {
        mutex.withLock {
            snapshot = snapshot?.update()?.copySecrets()
        }
    }
}

internal class GlobalSecretsDAOImpl internal constructor(
    private val queries: GlobalSecretsQueries,
    private val queriesContext: CoroutineContext
) : GlobalSecretsDAO {
    override suspend fun startupSecrets(): GlobalSecretsSnapshot = withContext(queriesContext) {
        val authSessions = mutableMapOf<UserIDEntity, GlobalAuthSessionEntity>()
        val proxyCredentials = mutableMapOf<UserIDEntity, GlobalProxyCredentialsEntity>()
        val dbSecrets = mutableMapOf<String, GlobalDbSecretEntity>()
        var pushRegistration: GlobalPushRegistrationEntity? = null

        queries.startupSecrets().awaitAsList().forEach { row ->
            when (row.kind) {
                STARTUP_SECRET_KIND_AUTH_SESSION -> {
                    val userId = requireNotNull(row.user_id)
                    authSessions[userId] = GlobalAuthSessionEntity(
                        userId = userId,
                        accessToken = requireNotNull(row.access_token),
                        refreshToken = requireNotNull(row.refresh_token),
                        tokenType = requireNotNull(row.token_type),
                        cookieLabel = row.cookie_label,
                        updatedAt = requireNotNull(row.updated_at)
                    )
                }
                STARTUP_SECRET_KIND_PROXY_CREDENTIALS -> {
                    val userId = requireNotNull(row.user_id)
                    proxyCredentials[userId] = GlobalProxyCredentialsEntity(
                        userId = userId,
                        username = requireNotNull(row.username),
                        password = requireNotNull(row.password),
                        updatedAt = requireNotNull(row.updated_at)
                    )
                }
                STARTUP_SECRET_KIND_PUSH_REGISTRATION -> {
                    pushRegistration = GlobalPushRegistrationEntity(
                        token = requireNotNull(row.token),
                        transport = requireNotNull(row.transport),
                        applicationId = requireNotNull(row.application_id),
                        updatedAt = requireNotNull(row.updated_at)
                    )
                }
                STARTUP_SECRET_KIND_DB_SECRET -> {
                    val alias = requireNotNull(row.alias)
                    dbSecrets[alias] = GlobalDbSecretEntity(
                        alias = alias,
                        secret = requireNotNull(row.secret).copyOf(),
                        version = requireNotNull(row.version),
                        createdAt = requireNotNull(row.created_at),
                        updatedAt = requireNotNull(row.updated_at)
                    )
                }
            }
        }

        GlobalSecretsSnapshot(
            authSessions = authSessions,
            proxyCredentials = proxyCredentials,
            pushRegistration = pushRegistration,
            dbSecrets = dbSecrets
        )
    }

    override suspend fun upsertAuthSession(authSession: GlobalAuthSessionEntity) {
        withContext(queriesContext) {
            queries.upsertAuthSession(
                userId = authSession.userId,
                accessToken = authSession.accessToken,
                refreshToken = authSession.refreshToken,
                tokenType = authSession.tokenType,
                cookieLabel = authSession.cookieLabel,
                updatedAt = authSession.updatedAt
            )
        }
    }

    override suspend fun authSession(userId: UserIDEntity): GlobalAuthSessionEntity? = withContext(queriesContext) {
        queries.authSessionByUserId(userId, ::GlobalAuthSessionEntity).awaitAsOneOrNull()
    }

    override suspend fun deleteAuthSession(userId: UserIDEntity) {
        withContext(queriesContext) {
            queries.deleteAuthSession(userId)
        }
    }

    override suspend fun upsertProxyCredentials(proxyCredentials: GlobalProxyCredentialsEntity) {
        withContext(queriesContext) {
            queries.upsertProxyCredentials(
                userId = proxyCredentials.userId,
                username = proxyCredentials.username,
                password = proxyCredentials.password,
                updatedAt = proxyCredentials.updatedAt
            )
        }
    }

    override suspend fun proxyCredentials(userId: UserIDEntity): GlobalProxyCredentialsEntity? = withContext(queriesContext) {
        queries.proxyCredentialsByUserId(userId, ::GlobalProxyCredentialsEntity).awaitAsOneOrNull()
    }

    override suspend fun deleteProxyCredentials(userId: UserIDEntity) {
        withContext(queriesContext) {
            queries.deleteProxyCredentials(userId)
        }
    }

    override suspend fun upsertPushRegistration(pushRegistration: GlobalPushRegistrationEntity) {
        withContext(queriesContext) {
            queries.upsertPushRegistration(
                token = pushRegistration.token,
                transport = pushRegistration.transport,
                applicationId = pushRegistration.applicationId,
                updatedAt = pushRegistration.updatedAt
            )
        }
    }

    override suspend fun pushRegistration(): GlobalPushRegistrationEntity? = withContext(queriesContext) {
        queries.pushRegistration(::GlobalPushRegistrationEntity).awaitAsOneOrNull()
    }

    override suspend fun deletePushRegistration() {
        withContext(queriesContext) {
            queries.deletePushRegistration()
        }
    }

    override suspend fun upsertDbSecret(dbSecret: GlobalDbSecretEntity) {
        require(dbSecret.secret.isNotEmpty()) {
            "Database secret must not be empty"
        }
        withContext(queriesContext) {
            queries.upsertDbSecret(
                alias = dbSecret.alias,
                secret = dbSecret.secret.copyOf(),
                version = dbSecret.version,
                createdAt = dbSecret.createdAt,
                updatedAt = dbSecret.updatedAt
            )
        }
    }

    override suspend fun dbSecret(alias: String): GlobalDbSecretEntity? = withContext(queriesContext) {
        queries.dbSecretByAlias(alias, ::GlobalDbSecretEntity)
            .awaitAsOneOrNull()
            ?.copySecret()
    }

    override suspend fun deleteDbSecret(alias: String) {
        withContext(queriesContext) {
            queries.deleteDbSecret(alias)
        }
    }

    private companion object {
        const val STARTUP_SECRET_KIND_AUTH_SESSION = "AUTH_SESSION"
        const val STARTUP_SECRET_KIND_PROXY_CREDENTIALS = "PROXY_CREDENTIALS"
        const val STARTUP_SECRET_KIND_PUSH_REGISTRATION = "PUSH_REGISTRATION"
        const val STARTUP_SECRET_KIND_DB_SECRET = "DB_SECRET"
    }
}

private fun GlobalDbSecretEntity.copySecret(): GlobalDbSecretEntity =
    copy(secret = secret.copyOf())
