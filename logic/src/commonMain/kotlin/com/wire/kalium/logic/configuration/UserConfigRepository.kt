package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
//     private val userConfigStorage: UserConfigStorage,
    private val metadataDAO: MetadataDAO
) : UserConfigRepository {
    override suspend fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(
                JsonSerializer().encodeToString(FileSharingStatus.serializer(), FileSharingStatus(status, isStatusChanged)),
                FILE_SHARING
            )
        }

    override suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus> =
        wrapStorageRequest {
            metadataDAO.valueByKey(FILE_SHARING)?.let {
                JsonSerializer().decodeFromString(FileSharingStatus.serializer(), it)
            }?.let {
                FileSharingStatus(it.isFileSharingEnabled, it.isStatusChanged)
            }
        }

    override suspend fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>> {
        return flowOf(isFileSharingEnabled())
    }

    override suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>) =
        wrapStorageRequest {
            metadataDAO.insertValue(
                JsonSerializer().encodeToString(
                    ClassifiedDomainsStatus.serializer(),
                    ClassifiedDomainsStatus(enabled, domains)
                ),
                ENABLE_CLASSIFIED_DOMAINS
            )
        }

    override suspend fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>> =
        flowOf(wrapStorageRequest {
            metadataDAO.valueByKey(ENABLE_CLASSIFIED_DOMAINS)?.let {
                JsonSerializer().decodeFromString(ClassifiedDomainsStatus.serializer(), it)
            }?.let {
                ClassifiedDomainsStatus(it.isClassifiedDomainsEnabled, it.trustedDomains)
            }
        }
        )

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
