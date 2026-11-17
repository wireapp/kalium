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

package com.wire.kalium.logic.data.mfa

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.feature.mfa.AuthenticatorApp
import com.wire.kalium.logic.feature.mfa.MFAEnrollment
import com.wire.kalium.logic.feature.mfa.MFAFailure
import com.wire.kalium.logic.feature.mfa.MFAOverview
import com.wire.kalium.logic.feature.mfa.RecoveryCodesStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryMFARepositoryTest {
    private val primary = AuthenticatorApp("primary", "Primary", LocalDate(2026, 10, 14))
    private val enabled = MFAOverview.Enabled(listOf(primary), RecoveryCodesStatus(3, 10))

    @Test
    fun setupRequiresAValidEnrollmentCodeAndNameBeforePublishingEnabledData() = runTest {
        val mfa = InMemoryMFARepository()
        assertNull(mfa.overview.value)
        mfa.refresh()
        assertEquals(MFAOverview.Disabled, mfa.overview.value)
        assertEquals(Either.Left(MFAFailure.INVALID_PASSWORD), mfa.startSetup(" "))
        val enrollment = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        assertTrue(enrollment.authenticatorUri.contains(enrollment.setupKey))
        assertEquals(Either.Left(MFAFailure.INVALID_ENROLLMENT), mfa.completeSetup(enrollment.id, "Phone"))
        assertEquals(Either.Left(MFAFailure.INVALID_CODE), mfa.verifySetupCode(enrollment.id, "000000"))
        val codes = assertIs<Either.Right<List<String>>>(mfa.verifySetupCode(enrollment.id, "111111")).value
        assertEquals(10, codes.size)
        assertEquals(Either.Right(codes), mfa.verifySetupCode(enrollment.id, "111111"))
        assertEquals(Either.Left(MFAFailure.INVALID_NAME), mfa.completeSetup(enrollment.id, " "))
        assertEquals(Either.Right(Unit), mfa.completeSetup(enrollment.id, " Phone "))
        val overview = assertIs<MFAOverview.Enabled>(mfa.overview.value)
        assertEquals("Phone", overview.authenticatorApps.single().name)
        assertEquals(RecoveryCodesStatus(10, 10), overview.recoveryCodesStatus)
        assertEquals(Either.Right(Unit), mfa.completeSetup(enrollment.id, "Phone"))
        mfa.refresh()
        assertEquals(overview, mfa.overview.value)
    }

    @Test
    fun additionRequiresItsOwnVerifiedEnrollmentAndDoesNotReplaceRecoveryCodes() = runTest {
        val mfa = InMemoryMFARepository(enabled)
        val enrollment = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        assertEquals(Either.Left(MFAFailure.INVALID_ENROLLMENT), mfa.finishAddingAuthenticator(enrollment.id, "Backup"))
        assertEquals(Either.Left(MFAFailure.INVALID_CODE), mfa.verifyAuthenticatorCode(enrollment.id, "000000"))
        assertEquals(Either.Right(Unit), mfa.verifyAuthenticatorCode(enrollment.id, "111111"))
        assertEquals(Either.Left(MFAFailure.INVALID_NAME), mfa.finishAddingAuthenticator(enrollment.id, " "))
        val added = assertIs<Either.Right<AuthenticatorApp>>(mfa.finishAddingAuthenticator(enrollment.id, " Backup ")).value
        val overview = assertIs<MFAOverview.Enabled>(mfa.overview.value)
        assertEquals(listOf(primary, added), overview.authenticatorApps)
        assertEquals("Backup", added.name)
        assertEquals(enabled.recoveryCodesStatus, overview.recoveryCodesStatus)
        assertEquals(Either.Right(added), mfa.finishAddingAuthenticator(enrollment.id, "Backup"))
        mfa.refresh()
        assertEquals(overview, mfa.overview.value)
    }

    @Test
    fun concurrentAdditionsCannotExceedTheFiveAppLimit() = runTest {
        val fourApps = enabled.copy(authenticatorApps = List(4) { primary.copy(id = "app-$it") })
        val mfa = InMemoryMFARepository(fourApps)
        val first = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        val second = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        mfa.verifyAuthenticatorCode(first.id, "111111")
        mfa.verifyAuthenticatorCode(second.id, "111111")
        val one = async { mfa.finishAddingAuthenticator(first.id, "One") }
        val two = async { mfa.finishAddingAuthenticator(second.id, "Two") }
        val results = listOf(one.await(), two.await())
        assertEquals(1, results.count { it is Either.Right })
        assertTrue(results.contains(Either.Left(MFAFailure.LIMIT_REACHED)))
        assertEquals(5, assertIs<MFAOverview.Enabled>(mfa.overview.value).authenticatorApps.size)
    }

    @Test
    fun concurrentRemovalsCannotRemoveTheFinalAuthenticator() = runTest {
        val mfa = InMemoryMFARepository(enabled.copy(authenticatorApps = listOf(primary, primary.copy(id = "backup"))))
        val first = async { mfa.removeAuthenticator("primary", "password", "111111") }
        val second = async { mfa.removeAuthenticator("backup", "password", "111111") }
        assertEquals(setOf(Either.Right(Unit), Either.Left(MFAFailure.LAST_APP)), setOf(first.await(), second.await()))
        val overview = assertIs<MFAOverview.Enabled>(mfa.overview.value)
        assertEquals(1, overview.authenticatorApps.size)
        assertEquals(enabled.recoveryCodesStatus, overview.recoveryCodesStatus)
    }

    @Test
    fun renameAndRemovalFailuresDoNotChangeAccountData() = runTest {
        val mfa = InMemoryMFARepository(enabled)
        mfa.refresh()
        assertEquals(Either.Left(MFAFailure.NOT_FOUND), mfa.renameAuthenticator("missing", "Name"))
        assertEquals(Either.Left(MFAFailure.INVALID_NAME), mfa.renameAuthenticator(primary.id, " "))
        assertEquals(Either.Left(MFAFailure.INVALID_CODE), mfa.removeAuthenticator(primary.id, "password", "000000"))
        assertEquals(Either.Left(MFAFailure.NOT_FOUND), mfa.removeAuthenticator("missing", "password", "111111"))
        assertEquals(Either.Left(MFAFailure.LAST_APP), mfa.removeAuthenticator(primary.id, "password", "111111"))
        assertEquals(enabled, mfa.overview.value)
        val renamed = primary.copy(name = "New name")
        assertEquals(Either.Right(renamed), mfa.renameAuthenticator(primary.id, " New name "))
        mfa.refresh()
        assertEquals(enabled.copy(authenticatorApps = listOf(renamed)), mfa.overview.value)
    }

    @Test
    fun regenerationPublishesCountsImmediatelyAndReturnsFreshCodesWithoutChangingApps() = runTest {
        val mfa = InMemoryMFARepository(enabled)
        assertEquals(Either.Left(MFAFailure.INVALID_PASSWORD), mfa.confirmPassword(" "))
        assertEquals(Either.Right(Unit), mfa.confirmPassword("password"))
        val first = assertIs<Either.Right<List<String>>>(mfa.generateRecoveryCodes()).value
        val second = assertIs<Either.Right<List<String>>>(mfa.generateRecoveryCodes()).value
        assertEquals(10, first.size)
        assertEquals(10, first.toSet().size)
        assertNotEquals(first, second)
        val expected = enabled.copy(recoveryCodesStatus = RecoveryCodesStatus(10, 10))
        assertEquals(expected, mfa.overview.value)
        mfa.refresh()
        assertEquals(expected, mfa.overview.value)
    }

    @Test
    fun turnOffInvalidatesPendingEnrollmentsAndStaleOperationsCannotRestoreMFA() = runTest {
        val mfa = InMemoryMFARepository(enabled)
        val enrollment = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        mfa.verifyAuthenticatorCode(enrollment.id, "111111")
        assertEquals(Either.Left(MFAFailure.INVALID_CODE), mfa.turnOff("password", "000000"))
        assertEquals(Either.Right(Unit), mfa.turnOff("password", "111111"))
        assertEquals(Either.Left(MFAFailure.MFA_DISABLED), mfa.finishAddingAuthenticator(enrollment.id, "Backup"))
        assertEquals(Either.Left(MFAFailure.INVALID_ENROLLMENT), mfa.completeSetup(enrollment.id, "Phone"))
        assertEquals(Either.Left(MFAFailure.MFA_DISABLED), mfa.renameAuthenticator(primary.id, "Name"))
        assertEquals(Either.Left(MFAFailure.MFA_DISABLED), mfa.generateRecoveryCodes())
        mfa.refresh()
        assertEquals(MFAOverview.Disabled, mfa.overview.value)
    }

    @Test
    fun overlappingRefreshesShareOneLoadAndMutationsWaitForItsResult() = runTest {
        val response = CompletableDeferred<MFAOverview>()
        var loads = 0
        val repository = InMemoryMFARepository(loadOverview = { loads++; response.await() })
        val refresh = launch { repository.refresh() }
        advanceTimeBy(500)
        runCurrent()
        repository.refresh()
        assertEquals(1, loads)
        val rename = async { repository.renameAuthenticator(primary.id, "Updated") }
        runCurrent()
        assertFalse(rename.isCompleted)
        response.complete(enabled)
        refresh.join()
        assertIs<Either.Right<AuthenticatorApp>>(rename.await())
        repository.refresh()
        assertEquals("Updated", assertIs<MFAOverview.Enabled>(repository.overview.value).authenticatorApps.single().name)
    }

    @Test
    fun refreshFailureRetainsCachedDataAndRetryCanRecover() = runTest {
        var fail = false
        val mfa = InMemoryMFARepository(loadOverview = {
            check(!fail) { "Offline" }
            enabled
        })
        mfa.refresh()
        fail = true
        assertIs<Either.Left<*>>(mfa.refresh())
        assertEquals(enabled, mfa.overview.value)
        fail = false
        assertEquals(Either.Right(Unit), mfa.refresh())
    }

    @Test
    fun cancellingRefreshPropagatesCancellationAndReleasesTheSharedLock() = runTest {
        val response = CompletableDeferred<MFAOverview>()
        val mfa = InMemoryMFARepository(loadOverview = { response.await() })
        val first = launch { mfa.refresh() }
        advanceTimeBy(500)
        runCurrent()
        first.cancel()
        first.join()
        assertTrue(first.isCancelled)
        assertNull(mfa.overview.value)
        response.complete(enabled)
        assertEquals(Either.Right(Unit), mfa.refresh())
        assertEquals(enabled, mfa.overview.value)
    }

    @Test
    fun cancellingAnOperationBeforeCompletionDoesNotPublishAMutation() = runTest {
        val mfa = InMemoryMFARepository(enabled)
        mfa.refresh()
        val pending = launch { mfa.renameAuthenticator(primary.id, "Updated") }
        runCurrent()
        pending.cancel()
        pending.join()
        assertEquals(enabled, mfa.overview.value)
        assertEquals(Either.Right(primary.copy(name = "Retry")), mfa.renameAuthenticator(primary.id, "Retry"))
    }
}
