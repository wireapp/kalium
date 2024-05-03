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

package com.wire.kalium.logic.feature.appVersioning

import com.wire.kalium.logic.configuration.appVersioning.AppVersionRepository

/**
 * Returns false if app needs to be updated and user should not be able app without it
 * true - otherwise
 */
interface CheckIfUpdateRequiredUseCase {
    suspend operator fun invoke(currentAppVersion: Int, blackListUrl: String): Boolean
}

internal class CheckIfUpdateRequiredUseCaseImpl(private val appVersionRepository: AppVersionRepository) : CheckIfUpdateRequiredUseCase {

    override suspend fun invoke(currentAppVersion: Int, blackListUrl: String): Boolean =
        appVersionRepository.isUpdateRequired(currentAppVersion, blackListUrl)
}
