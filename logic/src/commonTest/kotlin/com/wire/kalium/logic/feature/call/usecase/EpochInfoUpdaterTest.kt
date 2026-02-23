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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EpochInfo
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestCall.oneOnOneCallMetadata
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpochInfoUpdaterTest {
    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun breakDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenNoCallMetadata_whenInvoking_thenDoNotUpdateEpochInfo() = runTest {
        val (arrangement, epochInfoUpdater) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withCallMetadataReturning(null)
            .arrange()

        epochInfoUpdater(conversationId)

        verifySuspend(VerifyMode.not) {
            arrangement.callManager.updateEpochInfo(conversationId, any())
        }
    }

    @Test
    fun givenConversationIsNotMLS_whenInvoking_thenDoNotUpdateEpochInfo() = runTest {
        val (arrangement, epochInfoUpdater) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withCallMetadataReturning(callMetadata.copy(protocol = Conversation.ProtocolInfo.Proteus))
            .arrange()

        epochInfoUpdater(conversationId)

        verifySuspend(VerifyMode.not) {
            arrangement.callManager.updateEpochInfo(conversationId, any())
        }
    }

    @Test
    fun givenObserveEpochInfoReturnsFailure_whenInvoking_thenDoNotUpdateEpochInfo() = runTest {
        val (arrangement, epochInfoUpdater) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withCallMetadataReturning(callMetadata)
            .withObserveEpochInfoReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        epochInfoUpdater(conversationId)

        verifySuspend(VerifyMode.not) {
            arrangement.callManager.updateEpochInfo(conversationId, any())
        }
    }

    @Test
    fun givenObserveEpochInfoDoesNotReturnAnyEpochInfo_whenInvoking_thenDoNotUpdateEpochInfo() = runTest {
        val (arrangement, epochInfoUpdater) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withCallMetadataReturning(callMetadata)
            .withObserveEpochInfoReturning(Either.Right(emptyFlow()))
            .arrange()

        epochInfoUpdater(conversationId)

        verifySuspend(VerifyMode.not) {
            arrangement.callManager.updateEpochInfo(conversationId, any())
        }
    }

    @Test
    fun givenObserveEpochInfoReturnsValidEpochInfo_whenInvoking_thenUpdateEpochInfo() = runTest {
        val (arrangement, epochInfoUpdater) = Arrangement(testDispatcher.testKaliumDispatcher())
            .withCallMetadataReturning(callMetadata)
            .withObserveEpochInfoReturning(Either.Right(flowOf(epochInfo)))
            .arrange()

        epochInfoUpdater(conversationId)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callManager.updateEpochInfo(conversationId, epochInfo)
        }
    }

    inner class Arrangement(private val kaliumTestDispatcher: KaliumDispatcher) {
        internal val callManager = mock<CallManager>(MockMode.autoUnit)
        internal val callRepository = mock<CallRepository>()

        internal fun withCallMetadataReturning(callMetadata: CallMetadata?) = apply {
            everySuspend { callRepository.getCallMetadata(any()) } returns callMetadata
        }

        internal fun withObserveEpochInfoReturning(result: Either<CoreFailure, Flow<EpochInfo>>) = apply {
            everySuspend { callRepository.observeEpochInfo(any()) } returns result
        }

        internal fun arrange() = this to EpochInfoUpdaterImpl(
            callManager = lazy { callManager },
            callRepository = callRepository,
            dispatchers = kaliumTestDispatcher
        )
    }

    companion object {
        private val conversationId = ConversationId("conversationId", "wire.com")
        private val mlsProtocolInfo = Conversation.ProtocolInfo.MLS(
            groupId = GroupID("groupId"),
            groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            epoch = 1.toULong(),
            keyingMaterialLastUpdate = Instant.DISTANT_PAST,
            cipherSuite = CipherSuite.fromTag(1)
        )
        private val callMetadata = oneOnOneCallMetadata().copy(protocol = mlsProtocolInfo)
        private val epochInfo = EpochInfo(
            epoch = 1.toULong(),
            members = CallClientList(emptyList()),
            sharedSecret = byteArrayOf()
        )
    }
}
