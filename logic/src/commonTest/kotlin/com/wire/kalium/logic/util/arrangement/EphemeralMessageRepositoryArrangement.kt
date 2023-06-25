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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.message.EphemeralMessageRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock

internal interface EphemeralMessageRepositoryArrangement {
    @Mock
    val ephemeralMessageRepository: EphemeralMessageRepository

    fun withRecipientsForDeletedEphemeralSuccess(result: List<Recipient>)
}

internal open class EphemeralMessageRepositoryArrangementImpl : EphemeralMessageRepositoryArrangement {
    @Mock
    override val ephemeralMessageRepository: EphemeralMessageRepository = mock(EphemeralMessageRepository::class)
    override fun withRecipientsForDeletedEphemeralSuccess(result: List<Recipient>) {
        given(ephemeralMessageRepository)
            .suspendFunction(ephemeralMessageRepository::recipientsForDeletedEphemeral)
            .whenInvokedWith()
            .thenReturn(Either.Right(result))
    }
}
