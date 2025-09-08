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

import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
<<<<<<< HEAD
=======
import io.mockative.Mock
>>>>>>> d4d2f37283 (fix: Call Not Connect When SFT OneOnOne [WPB-19252] (#3611))
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock

interface IsE2EIEnabledUseCaseArrangement {
    val isE2EIEnabledUseCase: IsE2EIEnabledUseCase

    suspend fun withE2EIEnabledAndMLSEnabled(result: Boolean)
}

class IsE2EIEnabledUseCaseArrangementImpl : IsE2EIEnabledUseCaseArrangement {
        override val isE2EIEnabledUseCase: IsE2EIEnabledUseCase = mock(IsE2EIEnabledUseCase::class)

    override suspend fun withE2EIEnabledAndMLSEnabled(result: Boolean) {
        coEvery {
            isE2EIEnabledUseCase.invoke()
        }.returns(result)
    }
}
