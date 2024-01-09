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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.mapToRightOr
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface ObserveOtherUserSecurityClassificationLabelUseCase {
    /**
     * This operation will observe if a given user [otherUserId] is classified or not.
     *
     * @param otherUserId to classify
     * @return [SecurityClassificationType] with classification type
     */
    suspend operator fun invoke(otherUserId: UserId): Flow<SecurityClassificationType>
}

internal class ObserveOtherUserSecurityClassificationLabelUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val selfUserId: UserId,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveOtherUserSecurityClassificationLabelUseCase {

    override suspend fun invoke(otherUserId: UserId): Flow<SecurityClassificationType> = withContext(dispatchers.io) {
        if (otherUserId == selfUserId) {
            return@withContext flowOf(SecurityClassificationType.NONE)
        }
        getClassifiedDomainsStatus().map {
            when (it?.contains(otherUserId.domain)) {
                true -> SecurityClassificationType.CLASSIFIED
                false -> SecurityClassificationType.NOT_CLASSIFIED
                null -> SecurityClassificationType.NONE
            }
        }
    }

    private fun getClassifiedDomainsStatus(): Flow<List<String>?> = userConfigRepository.getClassifiedDomainsStatus()
        .mapToRightOr(null)
        .map { classifiedDomainsStatus ->
            if (classifiedDomainsStatus == null || !classifiedDomainsStatus.isClassifiedDomainsEnabled) {
                null
            } else {
                classifiedDomainsStatus.trustedDomains
            }
        }
}
