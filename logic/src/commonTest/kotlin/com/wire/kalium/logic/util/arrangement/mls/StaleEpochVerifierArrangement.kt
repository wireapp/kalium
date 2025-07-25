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
package com.wire.kalium.logic.util.arrangement.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

interface StaleEpochVerifierArrangement {

    val staleEpochVerifier: StaleEpochVerifier

    suspend fun withVerifyEpoch(result: Either<CoreFailure, Unit>)

}

class StaleEpochVerifierArrangementImpl : StaleEpochVerifierArrangement {

    override val staleEpochVerifier = mock(StaleEpochVerifier::class)

    override suspend fun withVerifyEpoch(result: Either<CoreFailure, Unit>) {
        coEvery {
            staleEpochVerifier.verifyEpoch(any(), any(), any(), any())
        }.returns(result)
    }
}
