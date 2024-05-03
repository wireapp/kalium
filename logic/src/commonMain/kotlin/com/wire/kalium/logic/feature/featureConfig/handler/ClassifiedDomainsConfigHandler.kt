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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.functional.Either

internal class ClassifiedDomainsConfigHandler internal constructor(
    private val userConfigRepository: UserConfigRepository
) {
    fun handle(classifiedDomainsConfig: ClassifiedDomainsModel): Either<CoreFailure, Unit> {
        val classifiedDomainsEnabled = classifiedDomainsConfig.status == Status.ENABLED
        return userConfigRepository.setClassifiedDomainsStatus(classifiedDomainsEnabled, classifiedDomainsConfig.config.domains)
    }
}
