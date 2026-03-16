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

package com.wire.kalium.logic.sync.slow

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationWithMessages
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadStoredMessage
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.clearInMemoryDatabase
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessageContent
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessagePayload
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceQualifiedId
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceText
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkApis
import com.wire.kalium.userstorage.di.DatabaseStorageType
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.PlatformUserStorageProvider
import kotlinx.coroutines.test.runTest
import okio.Sink
import okio.Source
import java.lang.reflect.Proxy
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncNomadMessagesDuringSlowSyncUseCaseTest {

    @Test
    fun givenMissingOrBlankNomadUrl_whenCheckingIsEnabled_thenReturnFalse() {
        assertFalse(newUseCase(nomadServiceUrl = null).isEnabled())
        assertFalse(newUseCase(nomadServiceUrl = "   ").isEnabled())
        assertTrue(newUseCase(nomadServiceUrl = "https://nomad.example.com").isEnabled())
    }

    @Test
    fun givenNomadMessages_whenInvoking_thenStoreMessagesAndReturnUnit() = runTest {
        val arrangement = Arrangement(
            nomadServiceUrl = "https://nomad.example.com",
            apiResponse = NetworkResponse.Success(
                NomadAllMessagesResponse(
                    conversations = listOf(
                        NomadConversationWithMessages(
                            conversation = Conversation(id = CONVERSATION_ID, domain = CONVERSATION_DOMAIN),
                            messages = listOf(
                                nomadStoredMessage(
                                    messageId = "msg-1",
                                    sender = SENDER_USER_ID,
                                    text = "hello from nomad"
                                )
                            )
                        )
                    )
                ),
                emptyMap(),
                200
            )
        )

        try {
            val result = arrangement.useCase()

            assertIs<Either.Right<Unit>>(result)

            val storedMessage = arrangement.database().messageDAO.getMessageById(
                id = "msg-1",
                conversationId = qid(CONVERSATION_ID)
            )
            val content = assertIs<MessageEntityContent.Text>(assertNotNull(storedMessage).content)
            assertEquals("hello from nomad", content.messageBody)
        } finally {
            arrangement.cleanup()
        }
    }

    @Test
    fun givenNomadApiFailure_whenInvoking_thenReturnFailure() = runTest {
        val arrangement = Arrangement(
            nomadServiceUrl = "https://nomad.example.com",
            apiResponse = NetworkResponse.Error(TestNetworkException.generic)
        )

        try {
            val result = arrangement.useCase()

            assertIs<Either.Left<*>>(result)
            assertIs<NetworkFailure.ServerMiscommunication>((result as Either.Left).value)
        } finally {
            arrangement.cleanup()
        }
    }

    private fun newUseCase(
        nomadServiceUrl: String?,
    ): SyncNomadMessagesDuringSlowSyncUseCaseImpl = SyncNomadMessagesDuringSlowSyncUseCaseImpl(
        selfUserId = UserId("self-user", "wire.test"),
        nomadServiceUrl = nomadServiceUrl,
        userStorageProvider = PlatformUserStorageProvider(),
        userAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider(),
        logger = KaliumLogger.disabled()
    )

    private class Arrangement(
        nomadServiceUrl: String?,
        apiResponse: NetworkResponse<NomadAllMessagesResponse>,
    ) {
        private val selfUserId = UserId("self-user-${nextId()}", "wire.test")
        private val selfNetworkUserId = com.wire.kalium.network.api.model.QualifiedID(selfUserId.value, selfUserId.domain)
        private val selfUserIdDao = UserIDEntity(selfUserId.value, selfUserId.domain)
        private val userStorageProvider = PlatformUserStorageProvider()
        private val userAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider()

        init {
            clearInMemoryDatabase(selfUserIdDao)
            userStorageProvider.getOrCreate(
                userId = selfUserId,
                platformUserStorageProperties = PlatformUserStorageProperties(
                    rootPath = "",
                    databaseInfo = DatabaseStorageType.InMemory
                ),
                shouldEncryptData = false,
                dbInvalidationControlEnabled = false
            )
            userAuthenticatedNetworkProvider.getOrCreate(selfNetworkUserId) {
                UserAuthenticatedNetworkApis(authenticatedNetworkContainer(FakeNomadDeviceSyncApi(apiResponse)))
            }
        }

        val useCase = SyncNomadMessagesDuringSlowSyncUseCaseImpl(
            selfUserId = selfUserId,
            nomadServiceUrl = nomadServiceUrl,
            userStorageProvider = userStorageProvider,
            userAuthenticatedNetworkProvider = userAuthenticatedNetworkProvider,
            logger = KaliumLogger.disabled()
        )

        fun database() = checkNotNull(userStorageProvider.get(selfUserId)).database

        fun cleanup() {
            userAuthenticatedNetworkProvider.remove(selfNetworkUserId)
            userStorageProvider.remove(selfUserId)
            clearInMemoryDatabase(selfUserIdDao)
        }
    }

    private class FakeNomadDeviceSyncApi(
        private val allMessagesResponse: NetworkResponse<NomadAllMessagesResponse>
    ) : NomadDeviceSyncApi {
        override suspend fun postMessageEvents(
            request: com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
        ): NetworkResponse<Unit> = error("Not needed in this test")

        override suspend fun getAllMessages(): NetworkResponse<NomadAllMessagesResponse> = allMessagesResponse

        override suspend fun getConversationMetadata(): NetworkResponse<NomadConversationMetadataResponse> =
            error("Not needed in this test")

        override suspend fun uploadCryptoState(
            clientId: String,
            backupSource: () -> Source,
            backupSize: Long
        ): NetworkResponse<Unit> = error("Not needed in this test")

        override suspend fun downloadCryptoState(tempBackupFileSink: Sink): NetworkResponse<Unit> =
            error("Not needed in this test")

        override suspend fun setLastDeviceId(deviceId: String): NetworkResponse<Unit> {
            error("Not needed in this test")
        }
    }

    private companion object {
        var id = 0

        fun nextId(): Int {
            id += 1
            return id
        }

        val SENDER_USER_ID = UserId("sender-user", "wire.test")
        const val CONVERSATION_ID = "conversation-id"
    }
}

private fun authenticatedNetworkContainer(
    nomadDeviceSyncApi: NomadDeviceSyncApi,
): AuthenticatedNetworkContainer = Proxy.newProxyInstance(
    AuthenticatedNetworkContainer::class.java.classLoader,
    arrayOf(AuthenticatedNetworkContainer::class.java)
) { _, method, args ->
    when (method.name) {
        "getNomadDeviceSyncApi" -> nomadDeviceSyncApi
        "clearCachedToken" -> Unit
        "toString" -> "TestAuthenticatedNetworkContainer"
        "hashCode" -> System.identityHashCode(nomadDeviceSyncApi)
        "equals" -> args?.firstOrNull() === nomadDeviceSyncApi
        else -> error("Unexpected call to ${method.name}.")
    }
} as AuthenticatedNetworkContainer

private fun nomadStoredMessage(
    messageId: String,
    sender: UserId,
    text: String,
): NomadStoredMessage {
    val payload = NomadDeviceMessagePayload(
        senderUserId = NomadDeviceQualifiedId(value = sender.value, domain = sender.domain),
        senderClientId = "sender-client",
        creationDate = 1_707_235_200_000,
        content = NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Text(
                NomadDeviceText(
                    text = text,
                    mentions = emptyList(),
                    quotedMessageId = null
                )
            )
        ),
        lastEditTime = null
    )
    return NomadStoredMessage(
        messageId = messageId,
        timestamp = 1_707_235_200,
        payload = Base64.Default.encode(payload.encodeToByteArray())
    )
}

private fun qid(value: String): QualifiedIDEntity = QualifiedIDEntity(value = value, domain = CONVERSATION_DOMAIN)

private const val CONVERSATION_DOMAIN = "wire.test"
