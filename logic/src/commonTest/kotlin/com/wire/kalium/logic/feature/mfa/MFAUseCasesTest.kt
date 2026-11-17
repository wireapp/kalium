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

package com.wire.kalium.logic.feature.mfa

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.mfa.InMemoryMFARepository
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
class MFAUseCasesTest {
    private val primary = AuthenticatorApp("primary", "Primary", LocalDate(2026, 10, 14))
    private val enabled = MFAOverview.Enabled(listOf(primary), RecoveryCodesStatus(3, 10))

    @Test
    fun setupRequiresAValidEnrollmentCodeAndNameBeforePublishingEnabledData() = runTest {
        val mfa = MFAScope(InMemoryMFARepository())
        assertNull(mfa.observeOverview().value)
        mfa.refreshOverview()
        assertEquals(MFAOverview.Disabled, mfa.observeOverview().value)
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
        val overview = assertIs<MFAOverview.Enabled>(mfa.observeOverview().value)
        assertEquals("Phone", overview.authenticatorApps.single().name)
        assertEquals(RecoveryCodesStatus(10, 10), overview.recoveryCodesStatus)
        assertEquals(Either.Right(Unit), mfa.completeSetup(enrollment.id, "Phone"))
        mfa.refreshOverview()
        assertEquals(overview, mfa.observeOverview().value)
    }

    @Test
    fun additionRequiresItsOwnVerifiedEnrollmentAndDoesNotReplaceRecoveryCodes() = runTest {
        val mfa = MFAScope(InMemoryMFARepository(enabled))
        val enrollment = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        assertEquals(Either.Left(MFAFailure.INVALID_ENROLLMENT), mfa.finishAddingAuthenticator(enrollment.id, "Backup"))
        assertEquals(Either.Left(MFAFailure.INVALID_CODE), mfa.verifyAuthenticatorCode(enrollment.id, "000000"))
        assertEquals(Either.Right(Unit), mfa.verifyAuthenticatorCode(enrollment.id, "111111"))
        assertEquals(Either.Left(MFAFailure.INVALID_NAME), mfa.finishAddingAuthenticator(enrollment.id, " "))
        val added = assertIs<Either.Right<AuthenticatorApp>>(mfa.finishAddingAuthenticator(enrollment.id, " Backup ")).value
        val overview = assertIs<MFAOverview.Enabled>(mfa.observeOverview().value)
        assertEquals(listOf(primary, added), overview.authenticatorApps)
        assertEquals("Backup", added.name)
        assertEquals(enabled.recoveryCodesStatus, overview.recoveryCodesStatus)
        assertEquals(Either.Right(added), mfa.finishAddingAuthenticator(enrollment.id, "Backup"))
        mfa.refreshOverview()
        assertEquals(overview, mfa.observeOverview().value)
    }

    @Test
    fun concurrentAdditionsCannotExceedTheFiveAppLimit() = runTest {
        val fourApps = enabled.copy(authenticatorApps = List(4) { primary.copy(id = "app-$it") })
        val mfa = MFAScope(InMemoryMFARepository(fourApps))
        val first = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        val second = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        mfa.verifyAuthenticatorCode(first.id, "111111")
        mfa.verifyAuthenticatorCode(second.id, "111111")
        val one = async { mfa.finishAddingAuthenticator(first.id, "One") }
        val two = async { mfa.finishAddingAuthenticator(second.id, "Two") }
        val results = listOf(one.await(), two.await())
        assertEquals(1, results.count { it is Either.Right })
        assertTrue(results.contains(Either.Left(MFAFailure.LIMIT_REACHED)))
        assertEquals(5, assertIs<MFAOverview.Enabled>(mfa.observeOverview().value).authenticatorApps.size)
    }

    @Test
    fun concurrentRemovalsCannotRemoveTheFinalAuthenticator() = runTest {
        val mfa = MFAScope(InMemoryMFARepository(enabled.copy(authenticatorApps = listOf(primary, primary.copy(id = "backup")))))
        val first = async { mfa.removeAuthenticator("primary", "password", "111111") }
        val second = async { mfa.removeAuthenticator("backup", "password", "111111") }
        assertEquals(setOf(Either.Right(Unit), Either.Left(MFAFailure.LAST_APP)), setOf(first.await(), second.await()))
        val overview = assertIs<MFAOverview.Enabled>(mfa.observeOverview().value)
        assertEquals(1, overview.authenticatorApps.size)
        assertEquals(enabled.recoveryCodesStatus, overview.recoveryCodesStatus)
    }

    @Test
    fun renameAndRemovalFailuresDoNotChangeAccountData() = runTest {
        val mfa = MFAScope(InMemoryMFARepository(enabled))
        mfa.refreshOverview()
        assertEquals(Either.Left(MFAFailure.NOT_FOUND), mfa.renameAuthenticator("missing", "Name"))
        assertEquals(Either.Left(MFAFailure.INVALID_NAME), mfa.renameAuthenticator(primary.id, " "))
        assertEquals(Either.Left(MFAFailure.INVALID_CODE), mfa.removeAuthenticator(primary.id, "password", "000000"))
        assertEquals(Either.Left(MFAFailure.NOT_FOUND), mfa.removeAuthenticator("missing", "password", "111111"))
        assertEquals(Either.Left(MFAFailure.LAST_APP), mfa.removeAuthenticator(primary.id, "password", "111111"))
        assertEquals(enabled, mfa.observeOverview().value)
        val renamed = primary.copy(name = "New name")
        assertEquals(Either.Right(renamed), mfa.renameAuthenticator(primary.id, " New name "))
        mfa.refreshOverview()
        assertEquals(enabled.copy(authenticatorApps = listOf(renamed)), mfa.observeOverview().value)
    }

    @Test
    fun regenerationPublishesCountsImmediatelyAndReturnsFreshCodesWithoutChangingApps() = runTest {
        val mfa = MFAScope(InMemoryMFARepository(enabled))
        assertEquals(Either.Left(MFAFailure.INVALID_PASSWORD), mfa.confirmPassword(" "))
        assertEquals(Either.Right(Unit), mfa.confirmPassword("password"))
        val first = assertIs<Either.Right<List<String>>>(mfa.generateRecoveryCodes()).value
        val second = assertIs<Either.Right<List<String>>>(mfa.generateRecoveryCodes()).value
        assertEquals(10, first.size)
        assertEquals(10, first.toSet().size)
        assertNotEquals(first, second)
        val expected = enabled.copy(recoveryCodesStatus = RecoveryCodesStatus(10, 10))
        assertEquals(expected, mfa.observeOverview().value)
        mfa.refreshOverview()
        assertEquals(expected, mfa.observeOverview().value)
    }

    @Test
    fun turnOffInvalidatesPendingEnrollmentsAndStaleOperationsCannotRestoreMFA() = runTest {
        val mfa = MFAScope(InMemoryMFARepository(enabled))
        val enrollment = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        mfa.verifyAuthenticatorCode(enrollment.id, "111111")
        assertEquals(Either.Left(MFAFailure.INVALID_CODE), mfa.turnOff("password", "000000"))
        assertEquals(Either.Right(Unit), mfa.turnOff("password", "111111"))
        assertEquals(Either.Left(MFAFailure.MFA_DISABLED), mfa.finishAddingAuthenticator(enrollment.id, "Backup"))
        assertEquals(Either.Left(MFAFailure.INVALID_ENROLLMENT), mfa.completeSetup(enrollment.id, "Phone"))
        assertEquals(Either.Left(MFAFailure.MFA_DISABLED), mfa.renameAuthenticator(primary.id, "Name"))
        assertEquals(Either.Left(MFAFailure.MFA_DISABLED), mfa.generateRecoveryCodes())
        mfa.refreshOverview()
        assertEquals(MFAOverview.Disabled, mfa.observeOverview().value)
    }

    @Test
    fun recreatingFeatureConsumersKeepsAccountDataAndSeparateAccountsStayIndependent() = runTest {
        val repository = InMemoryMFARepository(enabled)
        val firstConsumer = MFAScope(repository)
        firstConsumer.renameAuthenticator(primary.id, "Renamed")
        val reopened = MFAScope(repository)
        reopened.refreshOverview()
        assertEquals("Renamed", assertIs<MFAOverview.Enabled>(reopened.observeOverview().value).authenticatorApps.single().name)
        val anotherAccount = MFAScope(InMemoryMFARepository())
        anotherAccount.refreshOverview()
        assertEquals(MFAOverview.Disabled, anotherAccount.observeOverview().value)
        assertNotEquals(reopened.observeOverview().value, anotherAccount.observeOverview().value)
    }

    @Test
    fun overlappingRefreshesShareOneLoadAndMutationsWaitForItsResult() = runTest {
        val response = CompletableDeferred<MFAOverview>()
        var loads = 0
        val repository = InMemoryMFARepository(loadOverview = { loads++; response.await() })
        val firstConsumer = MFAScope(repository)
        val secondConsumer = MFAScope(repository)
        val refresh = launch { firstConsumer.refreshOverview() }
        advanceTimeBy(500)
        runCurrent()
        secondConsumer.refreshOverview()
        assertEquals(1, loads)
        val rename = async { secondConsumer.renameAuthenticator(primary.id, "Updated") }
        runCurrent()
        assertFalse(rename.isCompleted)
        response.complete(enabled)
        refresh.join()
        assertIs<Either.Right<AuthenticatorApp>>(rename.await())
        firstConsumer.refreshOverview()
        assertEquals("Updated", assertIs<MFAOverview.Enabled>(firstConsumer.observeOverview().value).authenticatorApps.single().name)
    }

    @Test
    fun refreshFailureRetainsCachedDataAndRetryCanRecover() = runTest {
        var fail = false
        val mfa = MFAScope(InMemoryMFARepository(loadOverview = {
            check(!fail) { "Offline" }
            enabled
        }))
        mfa.refreshOverview()
        fail = true
        assertIs<Either.Left<*>>(mfa.refreshOverview())
        assertEquals(enabled, mfa.observeOverview().value)
        fail = false
        assertEquals(Either.Right(Unit), mfa.refreshOverview())
    }

    @Test
    fun cancellingRefreshPropagatesCancellationAndReleasesTheSharedLock() = runTest {
        val response = CompletableDeferred<MFAOverview>()
        val mfa = MFAScope(InMemoryMFARepository(loadOverview = { response.await() }))
        val first = launch { mfa.refreshOverview() }
        advanceTimeBy(500)
        runCurrent()
        first.cancel()
        first.join()
        assertTrue(first.isCancelled)
        assertNull(mfa.observeOverview().value)
        response.complete(enabled)
        assertEquals(Either.Right(Unit), mfa.refreshOverview())
        assertEquals(enabled, mfa.observeOverview().value)
    }

    @Test
    fun cancellingAnOperationBeforeCompletionDoesNotPublishAMutation() = runTest {
        val mfa = MFAScope(InMemoryMFARepository(enabled))
        mfa.refreshOverview()
        val pending = launch { mfa.renameAuthenticator(primary.id, "Updated") }
        runCurrent()
        pending.cancel()
        pending.join()
        assertEquals(enabled, mfa.observeOverview().value)
        assertEquals(Either.Right(primary.copy(name = "Retry")), mfa.renameAuthenticator(primary.id, "Retry"))
    }
}
