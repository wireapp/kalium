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
package com.wire.kalium.logic.util.arrangement.usecase

import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock

internal interface ValidateAssetFileTypeUseCaseArrangement {
    val validateAssetFileTypeUseCase: ValidateAssetFileTypeUseCase

    fun withValidateAssetFileTypeReturning(result: Boolean)
}

internal class ValidateAssetFileTypeUseCaseArrangementImpl : ValidateAssetFileTypeUseCaseArrangement {
    override val validateAssetFileTypeUseCase = mock<ValidateAssetFileTypeUseCase>(mode = MockMode.autoUnit)

    override fun withValidateAssetFileTypeReturning(result: Boolean) {
        every { validateAssetFileTypeUseCase(any(), any(), any()) }.returns(result)
    }
}
