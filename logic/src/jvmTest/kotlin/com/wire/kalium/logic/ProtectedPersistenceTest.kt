/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.fakes.network.FakeNetworkStateObserver
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.session.DeleteSessionUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.ProxyCredentialsEntity
import com.wire.kalium.persistence.client.TransactionalAuthTokenStorage
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.protection.JvmStorageKeyStore
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkApis
import com.wire.kalium.userstorage.di.DatabaseStorageType
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.PlatformUserStorageProvider
import com.wire.kalium.util.InternalKaliumApi
import dev.mokkery.mock
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProtectedPersistenceTest {
    @Test
    @OptIn(InternalKaliumApi::class)
    fun coreLogicPersistsSessionTokensAndUsesItsOwnProtectedAccountStorage() = runBlocking {
        val root = createTempDirectory("kalium-protected-core").toFile()
        val protected = root.resolve("protected")
        val legacy = root.resolve("legacy")
        val keyStore = JvmStorageKeyStore { _, _ -> ByteArray(32) { 42 } }
        val user = UserId("test-account", "fixture.invalid")
        val entity = UserIDEntity(user.value, user.domain)
        val access = "fixture-core-session-access"
        val logic = CoreLogic(
            rootPath = protected.path,
            kaliumConfigs = KaliumConfigs(
                enableCalling = false,
                mockedRequests = emptyList(),
                mockNetworkStateObserver = FakeNetworkStateObserver(),
            ),
            userAgent = "protected-persistence-test",
            storageKeyStore = keyStore,
        )
        val database = CoreLogic::class.java.getDeclaredField("globalDatabaseBuilder").let { field ->
            field.isAccessible = true
            field.get(logic) as GlobalDatabaseBuilder
        }
        val legacyProvider = PlatformUserStorageProvider()
        val legacyNetwork = PlatformUserAuthenticatedNetworkProvider()
        val legacyApis = UserAuthenticatedNetworkApis(mock())
        val protectedApis = UserAuthenticatedNetworkApis(mock())
        val networkUserId = user.toApi()
        val scope = logic.getGlobalScope()
        try {
            legacyNetwork.getOrCreate(networkUserId) { legacyApis }
            assertSame(protectedApis, logic.userAuthenticatedNetworkProvider.getOrCreate(networkUserId) { protectedApis })
            val legacyStorage = legacyProvider.getOrCreate(
                user, PlatformUserStorageProperties(legacy.path, DatabaseStorageType.FiledBacked(legacy)),
                dbInvalidationControlEnabled = false,
            )
            val accountDirectory = protected.resolve("account")
            val protectedStorage = logic.userStorageProvider.getOrCreate(
                user, PlatformUserStorageProperties(protected.path, DatabaseStorageType.FiledBacked(accountDirectory)),
                dbInvalidationControlEnabled = false,
            )
            assertNotSame(legacyStorage, protectedStorage)
            assertTrue(legacy.resolve("main.db").readText(Charsets.ISO_8859_1).startsWith("SQLite format 3"))
            assertFalse(accountDirectory.resolve("main.db").readText(Charsets.ISO_8859_1).startsWith("SQLite format 3"))
            logic.userStorageProvider.remove(user)?.database?.close()

            val session = StoreSessionParam(
                serverConfigId = "fixture-host",
                ssoId = null,
                accountTokens = AccountTokens(user, access, "fixture-core-session-refresh", "Bearer", "fixture-cookie"),
                proxyCredentials = ProxyCredentials("fixture-proxy-user", "fixture-proxy-password"),
                isPersistentWebSocketEnabled = false,
            )
            assertEquals(AddAuthenticatedUserUseCase.Result.Success(user), scope.addAuthenticatedAccount(session))
            val tokens = assertIs<TransactionalAuthTokenStorage>(GlobalPrefProvider.protectedStorage(database).authTokenStorage)
            assertEquals(access, tokens.getToken(entity)?.accessToken)
            assertFalse(protected.resolve(FileNameUtil.appPrefFile()).exists())
            val globalFile = protected.resolve("global-storage/${FileNameUtil.globalDBName()}")
            assertFalse(access in globalFile.readText(Charsets.ISO_8859_1))

            val faultAfterCredentials = object : TransactionalAuthTokenStorage by tokens {
                override fun addOrReplace(authTokenEntity: AuthTokenEntity, proxyCredentialsEntity: ProxyCredentialsEntity?) {
                    tokens.addOrReplace(authTokenEntity, proxyCredentialsEntity)
                    error("Injected error after credential write")
                }

                override fun deleteToken(userId: UserIDEntity) {
                    tokens.deleteToken(userId)
                    error("Injected error after credential deletion")
                }
            }
            val faultyRepository = SessionDataSource(
                database.accountsDAO, faultAfterCredentials, database.serverConfigurationDAO
            )
            val otherUser = UserId("other-test-account", "fixture.invalid")
            val otherEntity = UserIDEntity(otherUser.value, otherUser.domain)
            assertIs<Either.Left<*>>(
                faultyRepository.storeSession(session.copy(accountTokens = session.accountTokens.copy(userId = otherUser)))
            )
            assertFalse(database.accountsDAO.doesAccountExist(otherEntity))
            assertNull(tokens.getToken(otherEntity))
            assertIs<Either.Left<*>>(faultyRepository.deleteSession(user))
            assertTrue(database.accountsDAO.doesAccountExist(entity))
            assertEquals(access, tokens.getToken(entity)?.accessToken)
            assertEquals(DeleteSessionUseCase.Result.Success, scope.deleteSession(user))
            assertNull(tokens.getToken(entity))
            assertNull(tokens.proxyCredentials(entity))
            assertNull(logic.userAuthenticatedNetworkProvider.get(networkUserId))
            assertSame(legacyApis, legacyNetwork.get(networkUserId))
        } finally {
            legacyNetwork.remove(networkUserId)
            legacyProvider.remove(user)?.database?.close()
            logic.userStorageProvider.remove(user)?.database?.close()
            scope.cancel()
            database.close()
            root.deleteRecursively()
        }
        Unit
    }
}
