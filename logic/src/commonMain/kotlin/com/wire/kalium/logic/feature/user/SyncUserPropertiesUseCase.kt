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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.properties.UserPropertyRepository

internal interface SyncUserPropertiesUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncUserPropertiesUseCaseImpl internal constructor(
    private val userPropertyRepository: UserPropertyRepository
) : SyncUserPropertiesUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> = userPropertyRepository.syncPropertiesStatuses()
}
