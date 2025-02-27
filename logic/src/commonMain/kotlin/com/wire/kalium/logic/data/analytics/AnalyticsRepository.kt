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
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.left
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.datetime.Instant

interface AnalyticsRepository {
    suspend fun getContactsAmountCached(): Either<StorageFailure, Int>
    suspend fun getTeamMembersAmountCached(): Either<CoreFailure, Int>
    suspend fun setContactsAmountCached(amount: Int)
    suspend fun setTeamMembersAmountCached(amount: Int)
    suspend fun getLastContactsDateUpdateDate(): Either<StorageFailure, Instant>
    suspend fun setContactsAmountCachingDate(date: Instant)
    suspend fun countContactsAmount(): Either<StorageFailure, Int>
    suspend fun countTeamMembersAmount(): Either<CoreFailure, Int>
}

internal class AnalyticsDataSource(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val selfTeamIdProvider: SelfTeamIdProvider,
) : AnalyticsRepository {

    override suspend fun getContactsAmountCached(): Either<StorageFailure, Int> = wrapStorageRequest {
        metadataDAO.valueByKey(CONTACTS_AMOUNT_KEY)?.toInt()
    }

    override suspend fun getTeamMembersAmountCached(): Either<CoreFailure, Int> = wrapStorageRequest {
        metadataDAO.valueByKey(TEAM_MEMBERS_AMOUNT_KEY)?.toInt()
    }

    override suspend fun setContactsAmountCached(amount: Int) =
        metadataDAO.insertValue(CONTACTS_AMOUNT_KEY, amount.toString())

    override suspend fun setTeamMembersAmountCached(amount: Int) =
        metadataDAO.insertValue(TEAM_MEMBERS_AMOUNT_KEY, amount.toString())

    override suspend fun getLastContactsDateUpdateDate(): Either<StorageFailure, Instant> = wrapStorageRequest {
        metadataDAO.valueByKey(LAST_CONTACTS_UPDATE_KEY)?.let { Instant.parse(it) }
    }

    override suspend fun setContactsAmountCachingDate(date: Instant) =
        metadataDAO.insertValue(LAST_CONTACTS_UPDATE_KEY, date.toString())

    override suspend fun countContactsAmount(): Either<StorageFailure, Int> = wrapStorageRequest {
        userDAO.countContactsAmount()
    }

    override suspend fun countTeamMembersAmount(): Either<CoreFailure, Int> = selfTeamIdProvider()
        .flatMap { teamId ->
            teamId?.let {
                wrapStorageRequest { userDAO.countTeamMembersAmount(it.value) }
            } ?: StorageFailure.DataNotFound.left()
        }

    companion object {
        internal const val CONTACTS_AMOUNT_KEY = "all_contacts_amount"
        internal const val TEAM_MEMBERS_AMOUNT_KEY = "team_members_amount"
        internal const val LAST_CONTACTS_UPDATE_KEY = "last_contacts_update_date"
    }
}
