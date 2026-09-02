/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal enum class MLSMembershipAuditState {
    NOT_REQUIRED,
    REQUIRED,
    REQUIRED_AFTER_SLOW_SYNC
}

internal interface MLSMembershipAuditRepository {
    fun observeAuditRequired(): Flow<Boolean>
    fun observeAuditState(): Flow<MLSMembershipAuditState>
    suspend fun isAuditRequired(): Either<StorageFailure, Boolean>
    suspend fun getAuditState(): Either<StorageFailure, MLSMembershipAuditState>
    suspend fun markAuditRequired(): Either<StorageFailure, Unit>
    suspend fun markAuditRequiredAfterSlowSync(): Either<StorageFailure, Unit>
    suspend fun clearAuditRequired(): Either<StorageFailure, Unit>
}

internal class MLSMembershipAuditRepositoryImpl(
    private val metadataDAO: MetadataDAO
) : MLSMembershipAuditRepository {

    override fun observeAuditRequired(): Flow<Boolean> =
        observeAuditState().map { it != MLSMembershipAuditState.NOT_REQUIRED }

    override fun observeAuditState(): Flow<MLSMembershipAuditState> =
        metadataDAO.valueByKeyFlow(MLS_MEMBERSHIP_AUDIT_REQUIRED_KEY).map { it.toAuditState() }

    override suspend fun isAuditRequired(): Either<StorageFailure, Boolean> =
        getAuditState().map { it != MLSMembershipAuditState.NOT_REQUIRED }

    override suspend fun getAuditState(): Either<StorageFailure, MLSMembershipAuditState> = wrapStorageRequest {
        metadataDAO.valueByKey(MLS_MEMBERSHIP_AUDIT_REQUIRED_KEY).toAuditState()
    }

    override suspend fun markAuditRequired(): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDAO.insertValue(value = AUDIT_REQUIRED_VALUE, key = MLS_MEMBERSHIP_AUDIT_REQUIRED_KEY)
    }

    override suspend fun markAuditRequiredAfterSlowSync(): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDAO.insertValue(value = AUDIT_REQUIRED_AFTER_SLOW_SYNC_VALUE, key = MLS_MEMBERSHIP_AUDIT_REQUIRED_KEY)
    }

    override suspend fun clearAuditRequired(): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDAO.deleteValue(key = MLS_MEMBERSHIP_AUDIT_REQUIRED_KEY)
    }

    private companion object {
        const val MLS_MEMBERSHIP_AUDIT_REQUIRED_KEY = "mlsMembershipAuditRequired"
        const val AUDIT_REQUIRED_VALUE = "true"
        const val AUDIT_REQUIRED_AFTER_SLOW_SYNC_VALUE = "required_after_slow_sync"

        fun String?.toAuditState(): MLSMembershipAuditState = when (this) {
            AUDIT_REQUIRED_VALUE -> MLSMembershipAuditState.REQUIRED
            AUDIT_REQUIRED_AFTER_SLOW_SYNC_VALUE -> MLSMembershipAuditState.REQUIRED_AFTER_SLOW_SYNC
            else -> MLSMembershipAuditState.NOT_REQUIRED
        }
    }
}
