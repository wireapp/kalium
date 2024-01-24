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
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.persistence.config.CRLUrlExpirationList
import com.wire.kalium.persistence.config.CRLWithExpiration
import com.wire.kalium.persistence.dao.MetadataDAO
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

interface CrlRepository {

    /**
     * Returns CRLs with expiration time.
     *
     * @return the [CRLUrlExpirationList] representing a list of CRLs with expiration time.
     */
    suspend fun getCRLs(): CRLUrlExpirationList?

    /**
     * Observes the last CRL check instant.
     *
     * @return A [Flow] of [Instant] objects representing the last CRL check instant.
     * It emits `null` if no CRL check has occurred.
     */
    suspend fun lastCrlCheckInstantFlow(): Flow<Instant?>

    /**
     * Sets the last CRL check date.
     *
     * @param instant The instant representing the date and time of the last CRL check.
     * @return Either a [StorageFailure] if the operation fails, or [Unit] if successful.
     */
    suspend fun setLastCRLCheckInstant(instant: Instant): Either<StorageFailure, Unit>

    suspend fun addOrUpdateCRL(url: String, timestamp: ULong)
    suspend fun getCurrentClientCrlUrl(): Either<CoreFailure, String>
    suspend fun getClientDomainCRL(url: String): Either<CoreFailure, ByteArray>
}

internal class CrlRepositoryDataSource(
    private val acmeApi: ACMEApi,
    private val metadataDAO: MetadataDAO,
    private val userConfigRepository: UserConfigRepository
) : CrlRepository {

    override suspend fun lastCrlCheckInstantFlow(): Flow<Instant?> =
        metadataDAO.valueByKeyFlow(CRL_CHECK_INSTANT_KEY).map { instant ->
            instant?.let { Instant.parse(it) }
        }

    override suspend fun setLastCRLCheckInstant(instant: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(instant.toString(), CRL_CHECK_INSTANT_KEY)
        }

    override suspend fun getCRLs(): CRLUrlExpirationList? =
        metadataDAO.getSerializable(CRL_LIST_KEY, CRLUrlExpirationList.serializer())

    override suspend fun addOrUpdateCRL(url: String, timestamp: ULong) {

        metadataDAO.getSerializable(CRL_LIST_KEY, CRLUrlExpirationList.serializer())
            ?.let { crlExpirationList ->
                val crlWithExpiration = crlExpirationList.cRLWithExpirationList.find {
                    it.url == url
                }
                val newCRLs = crlWithExpiration?.let { item ->
                    crlExpirationList.cRLWithExpirationList.map { current ->
                        if (current.url == url) {
                            return@map item.copy(expiration = timestamp)
                        } else {
                            return@map current
                        }
                    }
                } ?: run {
                    // add new CRL
                    crlExpirationList.cRLWithExpirationList.plus(
                        CRLWithExpiration(url, timestamp)
                    )
                }

                metadataDAO.putSerializable(
                    CRL_LIST_KEY,
                    CRLUrlExpirationList(newCRLs),
                    CRLUrlExpirationList.serializer()
                )
            }
    }

    override suspend fun getCurrentClientCrlUrl(): Either<CoreFailure, String> =
        userConfigRepository.getE2EISettings().map {
            (Url(it.discoverUrl).protocolWithAuthority)
        }

    override suspend fun getClientDomainCRL(url: String): Either<CoreFailure, ByteArray> =
        wrapApiRequest {
            acmeApi.getClientDomainCRL(url)
        }

    companion object {
        const val CRL_LIST_KEY = "crl_list_key"
        const val CRL_CHECK_INSTANT_KEY = "crl_check_instant_key"
    }
}
