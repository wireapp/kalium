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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.common.functional.map

interface FinalizeMLSClientAfterE2EIEnrollment {
    suspend fun invoke()
}

internal class FinalizeMLSClientAfterE2EIEnrollmentImpl(
    private val clientRepository: ClientRepository,
    private val joinExistingMLSConversationsUseCase: JoinExistingMLSConversationsUseCase
) : FinalizeMLSClientAfterE2EIEnrollment {
    override suspend fun invoke() {
        joinExistingMLSConversationsUseCase().map {
            clientRepository.clearClientRegistrationBlockedByE2EI()
        }
    }
}
