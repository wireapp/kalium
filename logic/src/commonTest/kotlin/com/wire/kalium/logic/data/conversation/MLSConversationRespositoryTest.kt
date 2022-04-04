package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.Member
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MLSConversationRespositoryTest {

    @Mock
    private val keyPackageRepository = mock(classOf<KeyPackageRepository>())

    @Mock
    private val mlsClientProvider = mock(classOf<MLSClientProvider>())

    @Mock
    private val conversationDAO = mock(classOf<ConversationDAO>())

    @Mock
    private val mlsMessageApi = mock(classOf<MLSMessageApi>())

    private lateinit var mlsConversationRepository: MLSConversationRepository

    @BeforeTest
    fun setup() {
        mlsConversationRepository = MLSConversationDataSource(
            keyPackageRepository,
            mlsClientProvider,
            mlsMessageApi,
            conversationDAO
        )
    }

    @Test
    fun givenConversation_whenCallingEstablishMLSGroup_thenGroupIsCreatedAndWelcomeMessageIsSent() = runTest {
        given(conversationDAO)
            .suspendFunction(conversationDAO::getAllMembers)
            .whenInvokedWith(anything())
            .then { flowOf(MEMBERS) }

        given(keyPackageRepository)
            .suspendFunction(keyPackageRepository::claimKeyPackages)
            .whenInvokedWith(anything())
            .then { Either.Right(listOf(KEY_PACKAGE)) }

        given(mlsClientProvider)
            .suspendFunction(mlsClientProvider::getMLSClient)
            .whenInvokedWith(anything())
            .then { Either.Right(MLS_CLIENT)}

        given(MLS_CLIENT)
            .function(MLS_CLIENT::createConversation)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Pair(HANDSHAKE, WELCOME))

        given(mlsMessageApi)
            .suspendFunction(mlsMessageApi::sendWelcomeMessage)
            .whenInvokedWith(anything())
            .then { NetworkResponse.Success(Unit, emptyMap(), 201) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::updateConversationGroupState)
            .whenInvokedWith(anything(), anything())
            .thenDoNothing()

        val result = mlsConversationRepository.establishMLSGroup(TestConversation.GROUP)

        result.shouldSucceed()

        verify(MLS_CLIENT)
            .function(MLS_CLIENT::createConversation)
            .with(eq(GROUP_ID), anything())
            .wasInvoked(once)

        verify(mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(WELCOME)) }
            .wasInvoked(once)
    }

    private companion object {
        val GROUP_ID = TestConversation.GROUP.groupId!!
        val MEMBERS = listOf(Member(TestUser.ENTITY_ID))
        val KEY_PACKAGE = KeyPackageDTO(
            "client1",
            "wire.com",
            "keyPackage",
            "keyPackageRef",
            "user1")
        val MLS_CLIENT = mock(classOf<MLSClient>())
        val WELCOME = "welcome".encodeToByteArray()
        val HANDSHAKE = "handshake".encodeToByteArray()
    }
}
