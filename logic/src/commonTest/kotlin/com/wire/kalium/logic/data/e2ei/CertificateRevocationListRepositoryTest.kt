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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepositoryDataSource.Companion.CRL_LIST_KEY
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.persistence.config.CRLWithExpiration
import com.wire.kalium.persistence.config.CRLUrlExpirationList
import com.wire.kalium.persistence.dao.MetadataDAO
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CertificateRevocationListRepositoryTest {

    @Test
    fun givenAnEmptyStoredList_whenUpdatingCRLs_thenAddNewCRL() = runTest {
        val (arrangement, crlRepository) = Arrangement()
            .withEmptyList()
            .arrange()

        crlRepository.addOrUpdateCRL(DUMMY_URL, TIMESTAMP)

        verify(arrangement.metadataDAO).coroutine {
            putSerializable(
                CRL_LIST_KEY,
                CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, TIMESTAMP))),
                CRLUrlExpirationList.serializer()
            )
        }.wasInvoked(once)
    }
    @Test
    fun givenNoStoredList_whenUpdatingCRLs_thenAddNewCRL() = runTest {
        val (arrangement, crlRepository) = Arrangement()
            .withNullCRLResult()
            .arrange()

        crlRepository.addOrUpdateCRL(DUMMY_URL, TIMESTAMP)

        verify(arrangement.metadataDAO).coroutine {
            putSerializable(
                CRL_LIST_KEY,
                CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, TIMESTAMP))),
                CRLUrlExpirationList.serializer()
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenPassedCRLExistsInStoredList_whenUpdatingCRLs_thenUpdateCurrentCRL() = runTest {
        val (arrangement, crlRepository) = Arrangement()
            .withCRLs()
            .arrange()

        crlRepository.addOrUpdateCRL(DUMMY_URL, TIMESTAMP2)

        verify(arrangement.metadataDAO).coroutine {
            putSerializable(
                CRL_LIST_KEY,
                CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, TIMESTAMP2))),
                CRLUrlExpirationList.serializer()
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenNewCRLUrl_whenUpdatingCRLs_thenAddNewCRL() = runTest {
        val (arrangement, crlRepository) = Arrangement()
            .withCRLs()
            .arrange()

        crlRepository.addOrUpdateCRL(DUMMY_URL2, TIMESTAMP)

        verify(arrangement.metadataDAO).coroutine {
            putSerializable(
                CRL_LIST_KEY,
                CRLUrlExpirationList(
                    listOf(
                        CRLWithExpiration(DUMMY_URL, TIMESTAMP),
                        CRLWithExpiration(DUMMY_URL2, TIMESTAMP)
                    )
                ),
                CRLUrlExpirationList.serializer()
            )
        }.wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val acmeApi = mock(classOf<ACMEApi>())

        @Mock
        val metadataDAO = mock(classOf<MetadataDAO>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        fun arrange() = this to CertificateRevocationListRepositoryDataSource(acmeApi, metadataDAO, userConfigRepository)

        suspend fun withEmptyList() = apply {
            given(metadataDAO).coroutine {
                metadataDAO.getSerializable(
                    CRL_LIST_KEY,
                    CRLUrlExpirationList.serializer()
                )
            }.thenReturn(CRLUrlExpirationList(listOf()))
        }
        suspend fun withNullCRLResult() = apply {
            given(metadataDAO).coroutine {
                metadataDAO.getSerializable(
                    CRL_LIST_KEY,
                    CRLUrlExpirationList.serializer()
                )
            }.thenReturn(null)
        }

        suspend fun withCRLs() = apply {
            given(metadataDAO).coroutine {
                metadataDAO.getSerializable(
                    CRL_LIST_KEY,
                    CRLUrlExpirationList.serializer()
                )
            }.thenReturn(CRLUrlExpirationList(listOf(CRLWithExpiration(DUMMY_URL, TIMESTAMP))))
        }
    }

    companion object {
        private const val DUMMY_URL = "https://dummy.url"
        private const val DUMMY_URL2 = "https://dummy-2.url"
        private val TIMESTAMP = 1234567890.toULong()
        private val TIMESTAMP2 = 5453222.toULong()
    }
}
