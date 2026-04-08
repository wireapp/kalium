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
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSender
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SendExistingAssetMessageUseCaseTest {

    @Test
    fun givenForwardableAsset_whenSending_thenShouldPersistAndSendAssetWithoutLocalData() = runTest(testDispatcher.default) {
        val assetContent = testAssetContent.copy(
            localData = AssetContent.LocalData(assetDataPath = "/tmp/image.jpg"),
        )
        val (arrangement, useCase) = Arrangement(this)
            .withFileSharingStatus(FileSharingStatus.Value.EnabledAll)
            .withReadReceiptsEnabled(false)
            .withCurrentClientSuccess()
            .withPersistMessageSuccess()
            .withSendMessageSuccess()
            .arrange()

        val result = useCase(TestConversation.ID, assetContent)

        assertIs<SendExistingAssetMessageResult.Success>(result)
        coVerify {
            arrangement.persistMessage.invoke(
                matches { message ->
                    message is Message.Regular &&
                        message.content is MessageContent.Asset &&
                        (message.content as MessageContent.Asset).value.localData == null &&
                        (message.content as MessageContent.Asset).value.remoteData == assetContent.remoteData
                }
            )
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.messageSender.sendMessage(
                matches { message ->
                    message is Message.Regular &&
                        message.content is MessageContent.Asset &&
                        (message.content as MessageContent.Asset).value.localData == null
                },
                any(),
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFileSharingDisabledByTeam_whenSending_thenShouldReturnFailureWithoutPersisting() = runTest(testDispatcher.default) {
        val (arrangement, useCase) = Arrangement(this)
            .withFileSharingStatus(FileSharingStatus.Value.Disabled)
            .arrange()

        val result = useCase(TestConversation.ID, testAssetContent)

        assertIs<SendExistingAssetMessageResult.Failure.DisabledByTeam>(result)
        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasNotInvoked()
    }

    private class Arrangement(private val coroutineScope: CoroutineScope) {
        val persistMessage: PersistMessageUseCase = mock(PersistMessageUseCase::class)
        private val provideClientId: CurrentClientIdProvider = mock(CurrentClientIdProvider::class)
        private val slowSyncRepository: SlowSyncRepository = mock(SlowSyncRepository::class)
        val messageSender: MessageSender = mock(MessageSender::class)
        private val messageSendFailureHandler: MessageSendFailureHandler = mock(MessageSendFailureHandler::class)
        private val userPropertyRepository: UserPropertyRepository = mock(UserPropertyRepository::class)
        private val selfDeleteTimer: ObserveSelfDeletionTimerSettingsForConversationUseCase =
            mock(ObserveSelfDeletionTimerSettingsForConversationUseCase::class)
        private val observeFileSharingStatus: ObserveFileSharingStatusUseCase = mock(ObserveFileSharingStatusUseCase::class)
        private val validateAssetFileUseCase: ValidateAssetFileTypeUseCase = mock(ValidateAssetFileTypeUseCase::class)

        private val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()

        init {
            every { slowSyncRepository.slowSyncStatus } returns completeStateFlow
            every { observeFileSharingStatus.invoke() } returns flowOf(FileSharingStatus(FileSharingStatus.Value.EnabledAll, false))
            every { validateAssetFileUseCase.invoke(any(), any(), any()) } returns true
        }

        fun withFileSharingStatus(status: FileSharingStatus.Value) = apply {
            every { observeFileSharingStatus.invoke() } returns flowOf(FileSharingStatus(status, false))
        }

        suspend fun withReadReceiptsEnabled(enabled: Boolean) = apply {
            coEvery { userPropertyRepository.getReadReceiptsStatus() } returns enabled
        }

        suspend fun withCurrentClientSuccess() = apply {
            coEvery { provideClientId.invoke() } returns TestClient.CLIENT_ID.right()
        }

        suspend fun withPersistMessageSuccess() = apply {
            coEvery { persistMessage.invoke(any()) } returns Unit.right()
        }

        suspend fun withSendMessageSuccess() = apply {
            coEvery { messageSender.sendMessage(any(), any()) } returns Unit.right()
        }

        suspend fun arrange(): Pair<Arrangement, SendExistingAssetMessageUseCase> {
            coEvery { selfDeleteTimer.invoke(any(), any()) } returns flowOf(SelfDeletionTimer.Disabled)

            return this to SendExistingAssetMessageUseCase(
                persistMessage = persistMessage,
                selfUserId = TestUser.USER_ID,
                provideClientId = provideClientId,
                slowSyncRepository = slowSyncRepository,
                messageSender = messageSender,
                messageSendFailureHandler = messageSendFailureHandler,
                userPropertyRepository = userPropertyRepository,
                selfDeleteTimer = selfDeleteTimer,
                observeFileSharingStatus = observeFileSharingStatus,
                validateAssetFileUseCase = validateAssetFileUseCase,
                dispatchers = testDispatcher,
                scope = coroutineScope,
            )
        }
    }

    companion object {
        private val testDispatcher = TestKaliumDispatcher

        private val testAssetContent = AssetContent(
            sizeInBytes = 128L,
            name = "image.jpg",
            mimeType = "image/jpeg",
            metadata = AssetContent.AssetMetadata.Image(width = 12, height = 10),
            remoteData = AssetContent.RemoteData(
                otrKey = byteArrayOf(1, 2, 3),
                sha256 = byteArrayOf(4, 5, 6),
                assetId = "asset-id",
                assetToken = "asset-token",
                assetDomain = "asset-domain",
                encryptionAlgorithm = null,
            ),
        )
    }
}
