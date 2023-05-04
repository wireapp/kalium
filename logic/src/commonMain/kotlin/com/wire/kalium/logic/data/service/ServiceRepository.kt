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
package com.wire.kalium.logic.data.service

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.ServiceDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ServiceRepository {
    suspend fun observeServiceDetails(
        serviceId: ServiceId,
        conversationId: ConversationId
    ) : Flow<ObservedServiceDetails?>
}

internal class ServiceDataSource internal constructor(
    private val serviceDAO: ServiceDAO,
    private val serviceMapper: ServiceMapper = MapperProvider.serviceMapper()
) : ServiceRepository {

    override suspend fun observeServiceDetails(
        serviceId: ServiceId,
        conversationId: ConversationId
    ): Flow<ObservedServiceDetails?> =
        serviceDAO.observeByIdAndConversation(
            id = serviceId.toDao(),
            conversationId = conversationId.toDao()
        ).map {
            it?.let {
                serviceMapper.fromDaoViewToObservedModel(dao = it)
            }
        }
}
