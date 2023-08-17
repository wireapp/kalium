/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

interface GetOtherUserSecurityClassificationLabelUseCase {
    /**
     * This operation will compute if a given user [otherUserId] is classified or not.
     *
     * @param otherUserId to classify
     * @return [SecurityClassificationType] with classification type
     */
    suspend operator fun invoke(otherUserId: UserId): SecurityClassificationType
}

internal class GetOtherUserSecurityClassificationLabelUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val selfUserId: UserId,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetOtherUserSecurityClassificationLabelUseCase {

    override suspend fun invoke(otherUserId: UserId): SecurityClassificationType = withContext(dispatchers.io) {
        if (otherUserId == selfUserId) {
            return@withContext SecurityClassificationType.NONE
        }
        return@withContext when (getClassifiedDomainsStatus()?.contains(otherUserId.domain)) {
            true -> SecurityClassificationType.CLASSIFIED
            false -> SecurityClassificationType.NOT_CLASSIFIED
            null -> SecurityClassificationType.NONE
        }
    }

    private suspend fun getClassifiedDomainsStatus(): List<String>? {
        val classifiedDomainsStatus = userConfigRepository.getClassifiedDomainsStatus().onlyRight().firstOrNull()
        return if (classifiedDomainsStatus == null || !classifiedDomainsStatus.isClassifiedDomainsEnabled) {
            null
        } else {
            classifiedDomainsStatus.trustedDomains
        }
    }
}
