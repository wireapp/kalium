/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.wire.kalium.logic.notificationextension

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.PlatformRootPathsProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.SecureRandom
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.util.configurePersistenceDebug
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.PlatformUserStorageProvider
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlin.concurrent.atomics.AtomicInt
import kotlin.io.encoding.Base64

/**
 * Minimal Apple account assembly for bounded notification-extension work.
 *
 * Unlike [com.wire.kalium.logic.CoreLogic], this type has no user-session provider, global call
 * manager, work scheduler or foreground application scope. It exists only while the spike still
 * consumes selected implementation classes from `:logic`; those classes remain extraction targets.
 */
@InternalKaliumApi("Temporary internal NSE assembly while the narrow provider graph is extracted from :logic")
public class NotificationExtensionCoreLogic(
    private val rootPath: String,
    private val keychainConfig: ApplePersistenceConfig,
    private val kaliumConfigs: KaliumConfigs,
    private val userAgent: String
) {
    private val closeState = AtomicInt(CORE_LOGIC_OPEN)
    private val globalPreferences = GlobalPrefProvider(
        keychainConfig = keychainConfig,
        shouldEncryptData = kaliumConfigs.shouldEncryptData()
    )
    private val globalDatabase = globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked("$rootPath/global-storage")),
        queriesContext = KaliumDispatcherImpl.io,
        passphrase = null
    )
    private val sessionRepository = SessionDataSource(
        accountsDAO = globalDatabase.accountsDAO,
        authTokenStorage = globalPreferences.authTokenStorage,
        serverConfigDAO = globalDatabase.serverConfigurationDAO
    )
    private val rootPathsProvider = PlatformRootPathsProvider(rootPath)
    private val userStorageProvider = PlatformUserStorageProvider()
    private val userAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider()

    init {
        configurePersistenceDebug(kaliumConfigs.isDebug)
    }

    /** Creates one passive authenticated receive/decrypt bridge for [userId]. */
    public fun createBridge(userId: UserId): NotificationExtensionLogicBridge =
        AppleNotificationExtensionLogicBridgeFactory(
            rootPath = rootPath,
            keychainConfig = keychainConfig,
            kaliumConfigs = kaliumConfigs,
            userAgent = userAgent,
            globalPreferences = globalPreferences,
            sessionRepository = sessionRepository,
            rootPathsProvider = rootPathsProvider,
            userStorageProvider = userStorageProvider,
            userAuthenticatedNetworkProvider = userAuthenticatedNetworkProvider
        ).create(userId)

    /**
     * Returns the independent SQLCipher key for one notification handoff scope.
     *
     * The shared Keychain configuration supplied at construction owns persistence and device-lock
     * accessibility. Callers must hold the account process lock so first-use generation cannot
     * race the foreground app.
     */
    public fun getOrCreateNotificationInboxDatabaseKey(
        userId: UserId,
        clientId: String
    ): String {
        require(clientId.isNotBlank())
        val alias = "$NOTIFICATION_INBOX_KEY_PREFIX:${notificationInboxScopeDigest(userId, clientId)}"
        return globalPreferences.passphraseStorage.getPassphrase(alias)
            ?: Base64.encode(SecureRandom().nextBytes(NOTIFICATION_INBOX_KEY_BYTES)).also { generated ->
                globalPreferences.passphraseStorage.setPassphrase(alias, generated)
            }
    }

    /**
     * Resolves the canonical APNs account UUID to exactly one qualified local Kalium account.
     *
     * APNs intentionally carries no backend domain. Ambiguous, missing, invalid, and storage-error
     * results all fail closed instead of selecting the foreground account.
     */
    public suspend fun resolveQualifiedUserId(userId: String): UserId? =
        when (val sessions = sessionRepository.allValidSessions()) {
            is Either.Left -> null
            is Either.Right ->
                sessions.value
                    .map { it.userId }
                    .singleOrNull { it.value.equals(userId, ignoreCase = true) }
        }

    /** Closes NSE-owned global persistence without deleting any account data. */
    public fun close() {
        closeNotificationExtensionCoreLogicOnce(closeState, globalDatabase::close)
    }
}

internal fun closeNotificationExtensionCoreLogicOnce(
    closeState: AtomicInt,
    closeGlobalDatabase: () -> Unit
) {
    if (!closeState.compareAndSet(CORE_LOGIC_OPEN, CORE_LOGIC_CLOSED)) return
    closeGlobalDatabase()
}

private fun notificationInboxScopeDigest(userId: UserId, clientId: String): String {
    val accountBytes = userId.toString().encodeToByteArray()
    val clientBytes = clientId.encodeToByteArray()
    val framed = ByteArray(LENGTH_BYTES + accountBytes.size + LENGTH_BYTES + clientBytes.size)
    writeBigEndianLength(framed, 0, accountBytes.size)
    accountBytes.copyInto(framed, LENGTH_BYTES)
    val clientLengthOffset = LENGTH_BYTES + accountBytes.size
    writeBigEndianLength(framed, clientLengthOffset, clientBytes.size)
    clientBytes.copyInto(framed, clientLengthOffset + LENGTH_BYTES)
    return calcSHA256(framed).toHexString()
}

private fun writeBigEndianLength(destination: ByteArray, offset: Int, value: Int) {
    destination[offset] = (value ushr 24).toByte()
    destination[offset + 1] = (value ushr 16).toByte()
    destination[offset + 2] = (value ushr 8).toByte()
    destination[offset + 3] = value.toByte()
}

private const val NOTIFICATION_INBOX_KEY_PREFIX = "notification_inbox_db_passphrase_v1"
private const val NOTIFICATION_INBOX_KEY_BYTES = 32
private const val LENGTH_BYTES = 4
private const val CORE_LOGIC_OPEN = 0
private const val CORE_LOGIC_CLOSED = 1
