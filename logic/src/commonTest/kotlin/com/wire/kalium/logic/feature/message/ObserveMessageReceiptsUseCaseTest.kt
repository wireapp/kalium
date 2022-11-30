package com.wire.kalium.logic.feature.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.message.receipt.DetailedReceipt
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.stub.ReceiptRepositoryStub
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ObserveMessageReceiptsUseCaseTest {

    @Test
    fun givenMessageAndConversationIdAndReceiptType_whenInvokingUseCase_thenShouldCallRepository() = runTest {
        // given
        var usedConversationId: ConversationId? = null
        var usedMessageId: String? = null
        var usedReceiptType: ReceiptType? = null

        val receiptRepository = object : ReceiptRepositoryStub() {
            override suspend fun observeMessageReceipts(
                conversationId: ConversationId,
                messageId: String,
                type: ReceiptType
            ): Flow<List<DetailedReceipt>> {
                usedConversationId = conversationId
                usedMessageId = messageId
                usedReceiptType = type

                return flowOf(listOf(DETAILED_RECEIPT))
            }
        }

        val observeMessageReceipts = ObserveMessageReceiptsUseCaseImpl(
            receiptRepository = receiptRepository
        )

        // when
        observeMessageReceipts(
            conversationId = CONVERSATION_ID,
            messageId = MESSAGE_ID,
            type = RECEIPT_TYPE
        ).test {
            // then
            val result = awaitItem()

            assertEquals(CONVERSATION_ID, usedConversationId)
            assertEquals(MESSAGE_ID, usedMessageId)
            assertEquals(RECEIPT_TYPE, usedReceiptType)

            assertContentEquals(listOf(DETAILED_RECEIPT), result)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val MESSAGE_ID = TestMessage.TEST_MESSAGE_ID
        val receiptDate = Clock.System.now()
        val CONVERSATION_ID = TestConversation.ID
        val RECEIPT_TYPE = ReceiptType.READ
        val USER_ID = QualifiedID(
            value = "userValue",
            domain = "userDomain"
        )
        val DETAILED_RECEIPT = DetailedReceipt(
            type = RECEIPT_TYPE,
            date = receiptDate,
            userSummary = UserSummary(
                userId = USER_ID,
                userName = "user name",
                userHandle = "userhandle",
                userPreviewAssetId = null,
                userType = UserType.INTERNAL,
                isUserDeleted = false,
                connectionStatus = ConnectionState.ACCEPTED,
                availabilityStatus = UserAvailabilityStatus.NONE
            )
        )
    }
}
