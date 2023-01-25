/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.persistence.globalDB

import com.wire.kalium.persistence.GlobalDBBaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.AccountInfoEntity
import com.wire.kalium.persistence.daokaliumdb.FullAccountEntity
import com.wire.kalium.persistence.daokaliumdb.PersistentWebSocketStatusEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.model.LogoutReason
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsDAOTest : GlobalDBBaseTest() {

    lateinit var db: GlobalDatabaseProvider

    @BeforeTest
    fun setUp() = runTest {
        deleteDatabase()
        db = createDatabase()

        with(SERVER_CONFIG) {
            db.serverConfigurationDAO.insert(
                ServerConfigurationDAO.InsertData(
                    id = id,
                    apiBaseUrl = links.api,
                    accountBaseUrl = links.accounts,
                    webSocketBaseUrl = links.webSocket,
                    blackListUrl = links.blackList,
                    teamsUrl = links.teams,
                    websiteUrl = links.website,
                    title = links.title,
                    isOnPremises = links.isOnPremises,
                    federation = metaData.federation,
                    domain = metaData.domain,
                    commonApiVersion = metaData.apiVersion,
                    apiProxyHost = links.apiProxy?.host,
                    apiProxyNeedsAuthentication = links.apiProxy?.needsAuthentication,
                    apiProxyPort = links.apiProxy?.port
                )
            )
        }
    }

    @Test
    fun givenNullSsoIdFields_thenReturnNull() = runTest {
        val accountWithNullSsoId =
            VALID_ACCOUNT.copy(info = AccountInfoEntity(UserIDEntity("user_null_sso", "domain"), null), ssoId = null)
        val accountWithSsoId = VALID_ACCOUNT.copy(
            info = AccountInfoEntity(UserIDEntity("user_sso", "domain"), null),
            ssoId = SsoIdEntity("sso_id", null, null)
        )

        db.accountsDAO.insertOrReplace(
            accountWithNullSsoId.info.userIDEntity,
            accountWithNullSsoId.ssoId,
            accountWithNullSsoId.serverConfigId,
            false
        )
        db.accountsDAO.insertOrReplace(
            accountWithSsoId.info.userIDEntity,
            accountWithSsoId.ssoId,
            accountWithSsoId.serverConfigId,
            false
        )

        db.accountsDAO.ssoId(accountWithNullSsoId.info.userIDEntity).also {
            assertNull(it)
        }
        db.accountsDAO.ssoId(accountWithSsoId.info.userIDEntity).also {
            assertEquals(accountWithSsoId.ssoId, it)
        }
    }

    @Test
    fun whenInsertingAccount_thenAccountIsInserted() = runTest {
        val account = VALID_ACCOUNT
        db.accountsDAO.insertOrReplace(account.info.userIDEntity, account.ssoId, account.serverConfigId, false)

        val insertedAccount = db.accountsDAO.fullAccountInfo(account.info.userIDEntity)
        assertEquals(account.info, insertedAccount?.info)
    }

    @Test
    fun whenCallingAllAccountList_thenAllStoredAccountsAreReturned() = runTest {
        val expectedList = insertAccounts().map { it.info }
        val actualList = db.accountsDAO.allAccountList()
        assertEquals(4, actualList.size)
        assertEquals(expectedList, actualList)
    }

    @Test
    fun whenCallingAllValidAccountList_thenOnlyValidAccountsAreReturned() = runTest {
        val expectedList = insertAccounts().filter { it.info.logoutReason == null }.map { it.info }
        val actualList = db.accountsDAO.allValidAccountList()
        assertEquals(3, actualList.size)

        assertEquals(expectedList, actualList)
    }

    private suspend fun insertAccounts(): List<FullAccountEntity> {
        val account1 = VALID_ACCOUNT
        val account2 = VALID_ACCOUNT
            .copy(info = VALID_ACCOUNT.info.copy(userIDEntity = UserIDEntity("user2", "domain2")))
        val account3 = VALID_ACCOUNT
            .copy(info = VALID_ACCOUNT.info.copy(userIDEntity = UserIDEntity("user3", "domain3")))
        val account4 = INVALID_ACCOUNT
        db.accountsDAO.insertOrReplace(account1.info.userIDEntity, account1.ssoId, account1.serverConfigId, false)
        db.accountsDAO.insertOrReplace(account2.info.userIDEntity, account2.ssoId, account2.serverConfigId, false)
        db.accountsDAO.insertOrReplace(account3.info.userIDEntity, account3.ssoId, account3.serverConfigId, false)
        db.accountsDAO.insertOrReplace(account4.info.userIDEntity, account4.ssoId, account4.serverConfigId, false)
        db.accountsDAO.markAccountAsInvalid(account4.info.userIDEntity, account4.info.logoutReason!!)

        return listOf(account1, account2, account3, account4)
    }

    @Test
    fun whenMarkingAccountAsInvalid_thenAccountIsMarkedAsInvalid() = runTest {
        val account = VALID_ACCOUNT
        db.accountsDAO.insertOrReplace(account.info.userIDEntity, account.ssoId, account.serverConfigId, false)
        db.accountsDAO.markAccountAsInvalid(account.info.userIDEntity, LogoutReason.SELF_SOFT_LOGOUT)

        val insertedAccount = db.accountsDAO.fullAccountInfo(account.info.userIDEntity)
        assertEquals(account.info.copy(logoutReason = LogoutReason.SELF_SOFT_LOGOUT), insertedAccount?.info)
    }

    @Test
    fun whenDeletingAccount_thenAccountIsDeleted() = runTest {
        val account = VALID_ACCOUNT
        db.accountsDAO.insertOrReplace(account.info.userIDEntity, account.ssoId, account.serverConfigId, false)
        val insertedAccount = db.accountsDAO.fullAccountInfo(account.info.userIDEntity)
        assertEquals(account, insertedAccount)

        db.accountsDAO.deleteAccount(account.info.userIDEntity)
        val deletedAccount = db.accountsDAO.fullAccountInfo(account.info.userIDEntity)
        assertEquals(null, deletedAccount)
    }

    @Test
    fun givenAccountNotInserted_whenCallindDoesAccountExists_thenFalseIsReturned() = runTest {
        val account = VALID_ACCOUNT
        val exists = db.accountsDAO.doesValidAccountExists(account.info.userIDEntity)
        assertEquals(false, exists)
    }

    @Test
    fun givenInvalidSession_whenCallindDoesValidAccountExists_thenFalseIsReturned() = runTest {
        val account = INVALID_ACCOUNT
        db.accountsDAO.insertOrReplace(account.info.userIDEntity, account.ssoId, account.serverConfigId, false)
        db.accountsDAO.markAccountAsInvalid(account.info.userIDEntity, account.info.logoutReason!!)
        val exists = db.accountsDAO.doesValidAccountExists(account.info.userIDEntity)
        assertEquals(false, exists)
    }

    private companion object {

        val VALID_ACCOUNT = FullAccountEntity(
            info = AccountInfoEntity(UserIDEntity("valid_user", "valid_domain"), null),
            serverConfigId = "server_config_id",
            ssoId = null,
            PersistentWebSocketStatusEntity(UserIDEntity("valid_user", "valid_domain"), false)
        )

        val INVALID_ACCOUNT = FullAccountEntity(
            info = AccountInfoEntity(UserIDEntity("invalid_user", "invalid_domain"), LogoutReason.REMOVED_CLIENT),
            serverConfigId = "server_config_id",
            ssoId = null,
            PersistentWebSocketStatusEntity(UserIDEntity("valid_user", "valid_domain"), false)
        )

        val SERVER_CONFIG = ServerConfigEntity(
            id = "server_config_id",
            links = ServerConfigEntity.Links(
                api = "api",
                accounts = "accounts",
                webSocket = "webSocket",
                blackList = "blackList",
                teams = "teams",
                website = "website",
                title = "title",
                isOnPremises = false,
                ServerConfigEntity.ApiProxy(true, "apiProxy", 8888)
            ),
            metaData = ServerConfigEntity.MetaData(
                federation = false,
                apiVersion = 420,
                domain = null
            )
        )
    }
}
