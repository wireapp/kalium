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

package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserConfigRepository {
    suspend fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
    suspend fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>>
    suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>): Either<StorageFailure, Unit>
    suspend fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>>
    suspend fun isMLSEnabled(): Either<StorageFailure, Boolean>
    suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean>
    suspend fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>>
    suspend fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit>
}

class UserConfigDataSource(
    private val metadataDAO: MetadataDAO
) : UserConfigRepository {
    override suspend fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertSerializable(FILE_SHARING, FileSharingStatus(status, isStatusChanged), FileSharingStatus.serializer())
        }

    override suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus> =
        wrapStorageRequest {
            metadataDAO.getSerializable(FILE_SHARING, FileSharingStatus.serializer())
        }

    override suspend fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>> {
        return metadataDAO.getSerializableFlow(FILE_SHARING, FileSharingStatus.serializer()).wrapStorageRequest()
    }

    override suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>) =
        wrapStorageRequest {
            metadataDAO.insertSerializable(
                ENABLE_CLASSIFIED_DOMAINS, ClassifiedDomainsStatus(enabled, domains), ClassifiedDomainsStatus.serializer()
            )
        }

    override suspend fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>> =
        metadataDAO.getSerializableFlow(ENABLE_CLASSIFIED_DOMAINS, ClassifiedDomainsStatus.serializer()).wrapStorageRequest()

    override suspend fun isMLSEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest { metadataDAO.valueByKey(ENABLE_MLS)?.toBoolean() }

    override suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { metadataDAO.insertValue(enabled.toString(), ENABLE_MLS) }

    override suspend fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(enabled.toString(), ENABLE_CONFERENCE_CALLING)
        }

    override suspend fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            metadataDAO.valueByKey(ENABLE_CONFERENCE_CALLING)?.toBoolean()
        }

    override suspend fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>> =
        metadataDAO.valueByKeyFlow(ENABLE_READ_RECEIPTS).map {
            it == null || it.isEmpty() || it.toBoolean()
        }.wrapStorageRequest()

    override suspend fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(enabled.toString(), ENABLE_READ_RECEIPTS)
        }

    private companion object {
        const val FILE_SHARING = "file_sharing"
        const val ENABLE_CLASSIFIED_DOMAINS = "enable_classified_domains"
        const val ENABLE_MLS = "enable_mls"
        const val ENABLE_CONFERENCE_CALLING = "enable_conference_calling"
        const val ENABLE_READ_RECEIPTS = "enable_read_receipts"
    }
}
