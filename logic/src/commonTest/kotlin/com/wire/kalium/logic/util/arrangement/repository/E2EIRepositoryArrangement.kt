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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock

internal interface E2EIRepositoryArrangement {
    val e2eiRepository: E2EIRepository

    fun withFetchACMECertificates()
}

internal class E2EIRepositoryArrangementImpl : E2EIRepositoryArrangement {
    @Mock
    override val e2eiRepository: E2EIRepository = mock(E2EIRepository::class)

    override fun withFetchACMECertificates() {
        given(e2eiRepository)
            .suspendFunction(e2eiRepository::fetchACMECertificates)
            .whenInvoked()
            .thenReturn(Either.Right(Unit))
    }
}
