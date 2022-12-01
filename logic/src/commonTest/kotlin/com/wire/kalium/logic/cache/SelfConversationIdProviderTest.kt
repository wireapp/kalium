package com.wire.kalium.logic.cache

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SelfConversationIdProviderTest {

    @Test
    fun givenMLSIsEnabled_thenMLSSelfConversationIsReturned() = runTest {
        val expected = Either.Right(Arrangement.MLS_SELF_CONVERSATION_ID)
        val (arrangement, selfConversationIdProvider) = Arrangement()
            .withMLSEnabled(true)
            .withMLSSelfConversationId(expected)
            .arrange()

        selfConversationIdProvider().also {
            assertIs<Either.Right<ConversationId>>(it)
            assertEquals(expected.value, it.value)
        }

        verify(arrangement.mlsSelfConversationIdProvider)
            .suspendFunction(arrangement.mlsSelfConversationIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSIsDisabled_thenProteusSelfConversationIsReturned() = runTest {
        val expected = Either.Right(Arrangement.PROTEUS_SELF_CONVERSATION_ID)
        val (arrangement, selfConversationIdProvider) = Arrangement()
            .withMLSEnabled(false)
            .withProteusSelfConversationId(expected)
            .arrange()

        selfConversationIdProvider().also {
            assertIs<Either.Right<ConversationId>>(it)
            assertEquals(expected.value, it.value)
        }

        verify(arrangement.proteusSelfConversationIdProvider)
            .suspendFunction(arrangement.proteusSelfConversationIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFailure_thenErrorIsPropagated() = runTest {
        val expected = Either.Left(StorageFailure.DataNotFound)
        val (arrangement, selfConversationIdProvider) = Arrangement()
            .withMLSEnabled(false)
            .withProteusSelfConversationId(expected)
            .arrange()

        selfConversationIdProvider().also {
            assertIs<Either.Left<StorageFailure>>(it)
            assertEquals(expected.value, it.value)
        }

        verify(arrangement.proteusSelfConversationIdProvider)
            .suspendFunction(arrangement.proteusSelfConversationIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val isMLSEnabled = mock(classOf<IsMLSEnabledUseCase>())

        @Mock
        val proteusSelfConversationIdProvider = mock(classOf<ProteusSelfConversationIdProvider>())

        @Mock
        val mlsSelfConversationIdProvider = mock(classOf<MLSSelfConversationIdProvider>())

        val selfConversationIdProvider: SelfConversationIdProvider = SelfConversationIdProviderImpl(
            isMLSEnabled,
            mlsSelfConversationIdProvider,
            proteusSelfConversationIdProvider
        )

        suspend fun withMLSEnabled(result: Boolean): Arrangement = apply {
            given(isMLSEnabled)
                .function(isMLSEnabled::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        suspend fun withProteusSelfConversationId(result: Either<StorageFailure, ConversationId>): Arrangement = apply {
            given(proteusSelfConversationIdProvider)
                .suspendFunction(proteusSelfConversationIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        suspend fun withMLSSelfConversationId(result: Either<StorageFailure, ConversationId>): Arrangement = apply {
            given(mlsSelfConversationIdProvider)
                .suspendFunction(mlsSelfConversationIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun arrange() = this to selfConversationIdProvider

        companion object {
            val MLS_SELF_CONVERSATION_ID = ConversationId("mls_self", "conv_domain")
            val PROTEUS_SELF_CONVERSATION_ID = ConversationId("proteus_self", "conv_domain")
        }
    }
}
