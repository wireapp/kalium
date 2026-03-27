/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
 * Checks whether the current session belongs to a valid nomad account
 * (i.e., the current account is valid and has a non-null nomad_service_url).
 */
public class IsCurrentSessionNomadAccountUseCase internal constructor(
    private val sessionRepository: SessionRepository
) {
    public suspend operator fun invoke(): Boolean {
        val currentAccountInfo = sessionRepository.currentSession().getOrElse { return false }
        if (!currentAccountInfo.isValid()) return false
        val account = sessionRepository.fullAccountInfo(currentAccountInfo.userId).getOrElse { return false }
        return !account.nomadServiceUrl.isNullOrBlank()
    }
}
