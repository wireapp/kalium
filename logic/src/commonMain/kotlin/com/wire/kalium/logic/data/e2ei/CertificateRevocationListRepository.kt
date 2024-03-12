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
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.persistence.config.CRLUrlExpirationList
import com.wire.kalium.persistence.config.CRLWithExpiration
import com.wire.kalium.persistence.dao.MetadataDAO
import io.ktor.http.URLBuilder
import io.ktor.http.authority

interface CertificateRevocationListRepository {

    /**
     * Returns CRLs with expiration time.
     *
     * @return the [CRLUrlExpirationList] representing a list of CRLs with expiration time.
     */
    suspend fun getCRLs(): CRLUrlExpirationList?
    suspend fun addOrUpdateCRL(url: String, timestamp: ULong)
    suspend fun getCurrentClientCrlUrl(): Either<CoreFailure, String>
    suspend fun getClientDomainCRL(url: String): Either<CoreFailure, ByteArray>
}

internal class CertificateRevocationListRepositoryDataSource(
    private val acmeApi: ACMEApi,
    private val metadataDAO: MetadataDAO,
    private val userConfigRepository: UserConfigRepository
) : CertificateRevocationListRepository {
    override suspend fun getCRLs(): CRLUrlExpirationList? =
        metadataDAO.getSerializable(CRL_LIST_KEY, CRLUrlExpirationList.serializer())

    override suspend fun addOrUpdateCRL(url: String, timestamp: ULong) {
        val newCRLUrls = metadataDAO.getSerializable(CRL_LIST_KEY, CRLUrlExpirationList.serializer())
                ?.let { crlExpirationList ->
                    val crlWithExpiration = crlExpirationList.cRLWithExpirationList.find {
                        it.url == url
                    }
                    crlWithExpiration?.let { item ->
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

                } ?: run {
                // add new CRL
                listOf(CRLWithExpiration(url, timestamp))
            }
        metadataDAO.putSerializable(
            CRL_LIST_KEY,
            CRLUrlExpirationList(newCRLUrls),
            CRLUrlExpirationList.serializer()
        )
    }

    override suspend fun getCurrentClientCrlUrl(): Either<CoreFailure, String> =
        userConfigRepository.getE2EISettings()
            .flatMap {
                if (!it.isRequired) E2EIFailure.Disabled.left()
                else if (it.discoverUrl == null) E2EIFailure.MissingDiscoveryUrl.left()
                else URLBuilder(it.discoverUrl).apply {
                    pathSegments.lastOrNull().let { segment ->
                        if (segment == null || segment != PATH_CRL) {
                            pathSegments = pathSegments + PATH_CRL
                        }
                    }
                }.authority.right()
            }

    override suspend fun getClientDomainCRL(url: String): Either<CoreFailure, ByteArray> =
        wrapApiRequest {
            acmeApi.getClientDomainCRL(url)
        }

    companion object {
        const val CRL_LIST_KEY = "crl_list_key"
        const val PATH_CRL = "crl"
    }
}
