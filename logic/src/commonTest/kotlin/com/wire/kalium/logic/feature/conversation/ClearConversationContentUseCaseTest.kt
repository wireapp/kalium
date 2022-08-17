package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import com.wire.kalium.logic.feature.conversation.ClearConversationContentUseCase.Result as Result

class ClearConversationContentUseCaseTest {

    @Test
    fun givenConversationHavingAssetMessagesAndTextMessages_whenInvoking_thenThoseAssetsWithMessagesAreRemoved() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withDeleteAsset(true)
            .withDeleteAllMessages(ConversationId("someValue", "someDomain"), true)
            .withGetAssetMessages(
                listOf(
                    TestMessage.assetMessage("1"),
                    TestMessage.assetMessage("2"),
                    TestMessage.assetMessage("3")
                )
            ).arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        with(arrangement) {
            verify(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .with(anything(), anything())
                .wasInvoked(Times(3))
        }

        assertIs<Result.Success>(result)
    }

    @Test
    fun givenConversationHasNoAssetsButOnlyTextMessages_whenInvoking_thenNoAssetsAreRemoved() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withDeleteAsset(true)
            .withDeleteAllMessages(ConversationId("someValue", "someDomain"), true)
            .withGetAssetMessages(emptyList())
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        with(arrangement) {
            verify(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .with(anything(), anything())
                .wasNotInvoked()
        }

        assertIs<Result.Success>(result)
    }

    @Test
    fun givenGettingAssetsMessagesFails_whenInvoking_thenFailureIsCorrectlyPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessages(emptyList(), false)
            .withDeleteAsset(true)
            .withDeleteAllMessages(ConversationId("someValue", "someDomain"), true)
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::getAssetMessages)
                .with(anything())
                .wasInvoked()

            verify(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .with(anything(), anything())
                .wasNotInvoked()

            verify(conversationRepository)
                .suspendFunction(conversationRepository::deleteAllMessages)
                .with(anything())
                .wasNotInvoked()
        }

        assertIs<Result.Failure>(result)
    }

    @Test
    fun givenDeletingAllMessagesFails_whenInvoking_thenFailureIsCorrectlyPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withDeleteAllMessages(ConversationId("someValue", "someDomain"), false)
            .withDeleteAsset(true)
            .withGetAssetMessages(
                listOf(
                TestMessage.assetMessage("1"),
                TestMessage.assetMessage("2"),
                TestMessage.assetMessage("3")
            ), true)
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        with(arrangement) {
            verify(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .with(anything(), anything())
                .wasInvoked()

            verify(conversationRepository)
                .suspendFunction(conversationRepository::deleteAllMessages)
                .with(anything())
                .wasInvoked()

            verify(conversationRepository)
                .suspendFunction(conversationRepository::getAssetMessages)
                .with(anything())
                .wasInvoked()
        }

        assertIs<Result.Failure>(result)
    }

    private class Arrangement {

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val assetRepository: AssetRepository = mock(classOf<AssetRepository>())

        fun withGetAssetMessages(assetMessages: List<Message> = emptyList(), isSuccessFull: Boolean = true): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getAssetMessages)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(assetMessages) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun withDeleteAsset(isSuccessFull: Boolean): Arrangement {
            given(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun withDeleteAllMessages(conversationId: ConversationId, isSuccessFull: Boolean): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::deleteAllMessages)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun arrange() = this to ClearConversationContentUseCase(conversationRepository, assetRepository)

    }

}
