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
package com.wire.kalium.logic.data.message.ephemeral

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsSenderUseCase
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.data.asset.AssetRepository
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class DeleteEphemeralMessageForSelfUserAsSenderUseCaseTest {

    @Test
    fun givenMessage_whenDeleting_thenMarkMessageAsDeleted() = runTest {
        val message = MESSAGE_REGULAR
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit))
                withGetMessageById(Either.Right(message))
            }

        useCase(message.conversationId, message.id).toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.markMessageAsDeleted(any(), any())
        }
    }

    @Test
    fun givenAssetMessage_whenDeleting_thenDeleteAssetLocally() = runTest {
        val assetContent = ASSET_IMAGE_CONTENT
        val message = MESSAGE_REGULAR.copy(
            content = MessageContent.Asset(assetContent)
        )
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit))
                withGetMessageById(Either.Right(message))
                withDeleteAssetLocally(Either.Right(Unit))
            }

        useCase(message.conversationId, message.id).toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.deleteAssetLocally(assetContent.remoteData.assetId)
        }
    }

    private companion object {
        val CURRENT_CLIENT_ID = ClientId("currentClientId")
        val ASSET_CONTENT_REMOTE_DATA = AssetContent.RemoteData(
            otrKey = ByteArray(0),
            sha256 = ByteArray(16),
            assetId = "asset-id",
            assetToken = "==some-asset-token",
            assetDomain = "some-asset-domain.com",
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
        )
        val ASSET_IMAGE_CONTENT = AssetContent(
            0L,
            "name",
            "image/jpg",
            AssetContent.AssetMetadata.Image(100, 100),
            ASSET_CONTENT_REMOTE_DATA
        )
        val MESSAGE_REGULAR = Message.Regular(
            id = "messageId",
            content = MessageContent.Text("text"),
            conversationId = ConversationId("conversationId", "conversationDomain"),
            date = Instant.DISTANT_FUTURE,
            senderUserId = UserId("senderId", "senderDomain"),
            senderClientId = CURRENT_CLIENT_ID,
            status = Message.Status.Pending,
            editStatus = Message.EditStatus.NotEdited,
            isSelfMessage = true
        )
    }

    private class Arrangement {
        val messageRepository = mock<MessageRepository>()
        val assetRepository = mock<AssetRepository>()

        private val useCase: DeleteEphemeralMessageForSelfUserAsSenderUseCase =
            DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl(
                messageRepository = messageRepository,
                assetRepository = assetRepository,
            )

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DeleteEphemeralMessageForSelfUserAsSenderUseCase> {
            block()
            return this to useCase
        }

        suspend fun withMarkAsDeleted(result: Either<com.wire.kalium.common.error.StorageFailure, Unit>) = apply {
            everySuspend { messageRepository.markMessageAsDeleted(any(), any()) } returns result
        }

        suspend fun withGetMessageById(result: Either<com.wire.kalium.common.error.StorageFailure, Message>) = apply {
            everySuspend { messageRepository.getMessageById(any(), any()) } returns result
        }

        suspend fun withDeleteAssetLocally(result: Either<com.wire.kalium.common.error.CoreFailure, Unit>) = apply {
            everySuspend { assetRepository.deleteAssetLocally(any()) } returns result
        }
    }
}
