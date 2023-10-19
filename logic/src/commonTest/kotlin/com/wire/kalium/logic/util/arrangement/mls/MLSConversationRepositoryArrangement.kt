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
package com.wire.kalium.logic.util.arrangement.mls

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.any
import io.mockative.given
import io.mockative.mock

interface MLSConversationRepositoryArrangement {
    val mlsConversationRepository: MLSConversationRepository

    fun withIsGroupOutOfSync(result: Either<CoreFailure, Boolean>)
}

class MLSConversationRepositoryArrangementImpl : MLSConversationRepositoryArrangement {
    override val mlsConversationRepository = mock(MLSConversationRepository::class)

    override fun withIsGroupOutOfSync(result: Either<CoreFailure, Boolean>) {
        given(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::isGroupOutOfSync)
            .whenInvokedWith(any(), any())
            .thenReturn(result)
    }
}
