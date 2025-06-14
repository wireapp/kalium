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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.persistence.config.CRLUrlExpirationList
import com.wire.kalium.persistence.config.CRLWithExpiration
import com.wire.kalium.persistence.dao.MetadataDAO
import io.mockative.Mockable

@Mockable
internal interface CertificateRevocationListRepository {

    /**
     * Returns CRLs with expiration time.
     *
     * @return the [CRLUrlExpirationList] representing a list of CRLs with expiration time.
     */
    suspend fun getCRLs(): CRLUrlExpirationList?
    suspend fun addOrUpdateCRL(url: String, timestamp: ULong)
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

    override suspend fun getClientDomainCRL(url: String): Either<CoreFailure, ByteArray> =
        wrapApiRequest {
            val proxyUrl = userConfigRepository.getE2EISettings()
                .map { if (!it.shouldUseProxy || it.crlProxy.isNullOrBlank()) null else it.crlProxy }
                .getOrNull()

            acmeApi.getClientDomainCRL(url, proxyUrl)
        }

    companion object {
        const val CRL_LIST_KEY = "crl_list_key"
    }
}
