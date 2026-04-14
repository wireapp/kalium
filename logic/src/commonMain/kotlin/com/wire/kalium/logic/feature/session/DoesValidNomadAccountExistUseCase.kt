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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.data.session.SessionRepository

/**
 * This use case checks whether a valid nomad account exists (i.e., an account with a non-null nomad_service_url
 * that has not been logged out).
 */
public class DoesValidNomadAccountExistUseCase internal constructor(
    private val sessionRepository: SessionRepository
) {
    public suspend operator fun invoke(): Boolean =
        sessionRepository.doesValidNomadAccountExist().getOrElse { false }
}
