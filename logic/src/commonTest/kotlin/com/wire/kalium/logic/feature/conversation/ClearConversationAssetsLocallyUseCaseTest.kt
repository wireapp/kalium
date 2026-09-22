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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearConversationAssetsLocallyUseCaseTest {
    @Test
    fun assetFailureDoesNotSkipRemainingAssetsAndIsReturned() = runTest {
        val messages = mock<MessageRepository>()
        val assets = mock<AssetRepository>()
        val id = ConversationId("conversation", "example.com")
        val failure = Either.Left(CoreFailure.Unknown(null))
        everySuspend { messages.getAllAssetIdsFromConversationId(id) } returns Either.Right(listOf("first", "second"))
        everySuspend { assets.deleteAssetLocally("first") } returns failure
        everySuspend { assets.deleteAssetLocally("second") } returns Either.Right(Unit)

        assertEquals(failure, ClearConversationAssetsLocallyUseCaseImpl(messages, assets)(id))

        verifySuspend(VerifyMode.exactly(1)) { assets.deleteAssetLocally("second") }
    }

    @Test
    fun emptyConversationNeedsNoAssetCleanup() = runTest {
        val messages = mock<MessageRepository>()
        val assets = mock<AssetRepository>()
        val id = ConversationId("conversation", "example.com")
        everySuspend { messages.getAllAssetIdsFromConversationId(id) } returns Either.Right(emptyList())

        assertEquals(Either.Right(Unit), ClearConversationAssetsLocallyUseCaseImpl(messages, assets)(id))

        verifySuspend(VerifyMode.not) { assets.deleteAssetLocally(any()) }
    }
}
