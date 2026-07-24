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

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.persistence.dao.MetadataDAO
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSMembershipAuditRepositoryTest {

    @Test
    fun givenPersistedMarker_whenReadingAndObserving_thenAuditIsRequired() = runTest {
        val metadataDAO = mock<MetadataDAO>()
        everySuspend { metadataDAO.valueByKey(any()) } returns "true"
        every { metadataDAO.valueByKeyFlow(any()) } returns flowOf(null, "true")
        val repository = MLSMembershipAuditRepositoryImpl(metadataDAO)

        assertEquals(true, repository.isAuditRequired().getOrElse(false))
        assertEquals(listOf(false, true), repository.observeAuditRequired().toList())
        assertEquals(
            listOf(MLSMembershipAuditState.NOT_REQUIRED, MLSMembershipAuditState.REQUIRED),
            repository.observeAuditState().toList()
        )
    }

    @Test
    fun whenMarkingAndClearingAuditRequirement_thenMetadataIsPersistedAndDeleted() = runTest {
        val metadataDAO = mock<MetadataDAO>()
        everySuspend { metadataDAO.insertValue(any(), any()) } returns Unit
        everySuspend { metadataDAO.deleteValue(any()) } returns Unit
        val repository = MLSMembershipAuditRepositoryImpl(metadataDAO)

        repository.markAuditRequired()
        repository.clearAuditRequired()

        verifySuspend(VerifyMode.exactly(1)) {
            metadataDAO.insertValue(eq("true"), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            metadataDAO.deleteValue(any())
        }
    }

    @Test
    fun whenMarkingAuditAfterSlowSync_thenDeferredStateIsPersisted() = runTest {
        val metadataDAO = mock<MetadataDAO>()
        everySuspend { metadataDAO.insertValue(any(), any()) } returns Unit
        val repository = MLSMembershipAuditRepositoryImpl(metadataDAO)

        repository.markAuditRequiredAfterSlowSync()

        verifySuspend(VerifyMode.exactly(1)) {
            metadataDAO.insertValue(eq("required_after_slow_sync"), any())
        }
    }
}
