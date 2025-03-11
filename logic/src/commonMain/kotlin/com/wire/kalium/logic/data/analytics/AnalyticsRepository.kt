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
package com.wire.kalium.logic.data.analytics

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.datetime.Instant

interface AnalyticsRepository {
    suspend fun getContactsAmountCached(): Either<StorageFailure, Int>
    suspend fun getTeamMembersAmountCached(): Either<CoreFailure, Int>
    suspend fun setContactsAmountCached(amount: Int)
    suspend fun setTeamMembersAmountCached(amount: Int)
    suspend fun getLastContactsDateUpdateDate(): Either<StorageFailure, Instant>
    suspend fun setLastContactsDateUpdateDate(date: Instant)
    suspend fun countContactsAmount(): Either<StorageFailure, Int>
    suspend fun countTeamMembersAmount(teamId: TeamId): Either<CoreFailure, Int>
}

internal class AnalyticsDataSource(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfUserId: UserId
) : AnalyticsRepository {

    override suspend fun getContactsAmountCached(): Either<StorageFailure, Int> = wrapStorageRequest {
        metadataDAO.valueByKey(CONTACTS_AMOUNT_KEY)?.toInt()
    }

    override suspend fun getTeamMembersAmountCached(): Either<CoreFailure, Int> = wrapStorageRequest {
        metadataDAO.valueByKey(TEAM_MEMBERS_AMOUNT_KEY)?.toInt()
    }

    override suspend fun setContactsAmountCached(amount: Int) =
        metadataDAO.insertValue(
            value = amount.toString(),
            key = CONTACTS_AMOUNT_KEY
        )

    override suspend fun setTeamMembersAmountCached(amount: Int) =
        metadataDAO.insertValue(
            value = amount.toString(),
            key = TEAM_MEMBERS_AMOUNT_KEY
        )

    override suspend fun getLastContactsDateUpdateDate(): Either<StorageFailure, Instant> = wrapStorageRequest {
        metadataDAO.valueByKey(LAST_CONTACTS_UPDATE_KEY)?.let {
            Instant.parse(it)
        }
    }

    override suspend fun setLastContactsDateUpdateDate(date: Instant) {
        metadataDAO.insertValue(
            value = date.toString(),
            key = LAST_CONTACTS_UPDATE_KEY
        )
    }

    override suspend fun countContactsAmount(): Either<StorageFailure, Int> = wrapStorageRequest {
        userDAO.countContactsAmount(selfUserId.toDao())
    }

    override suspend fun countTeamMembersAmount(teamId: TeamId): Either<CoreFailure, Int> =
        wrapStorageRequest { userDAO.countTeamMembersAmount(teamId.value, selfUserId.toDao()) }

    companion object {
        internal const val CONTACTS_AMOUNT_KEY = "all_contacts_amount"
        internal const val TEAM_MEMBERS_AMOUNT_KEY = "team_members_amount"
        internal const val LAST_CONTACTS_UPDATE_KEY = "last_contacts_update_date"
    }
}
