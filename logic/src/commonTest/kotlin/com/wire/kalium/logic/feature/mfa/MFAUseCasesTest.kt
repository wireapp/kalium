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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MFAUseCasesTest {
    @Test
    fun publicUseCasesShareOverviewAcrossTheMFASettingsLifecycle() = runTest {
        val mfa = MFAScope(InMemoryMFARepository())
        val overview = mfa.observeOverview()
        assertNull(overview.value)
        assertEquals(Either.Right(Unit), mfa.refreshOverview())
        assertEquals(MFAOverview.Disabled, overview.value)

        val initial = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        val recoveryCodes = assertIs<Either.Right<List<String>>>(mfa.verifySetupCode(initial.id, "111111")).value
        assertEquals(Either.Right(Unit), mfa.completeSetup(initial.id, "Phone"))
        val enabled = assertIs<MFAOverview.Enabled>(overview.value)
        assertEquals(initial.id, enabled.authenticatorApps.single().id)
        assertEquals(RecoveryCodesStatus(recoveryCodes.size, recoveryCodes.size), enabled.recoveryCodesStatus)

        val additional = assertIs<Either.Right<MFAEnrollment>>(mfa.startSetup("password")).value
        assertEquals(Either.Right(Unit), mfa.verifyAuthenticatorCode(additional.id, "111111"))
        val added = assertIs<Either.Right<AuthenticatorApp>>(mfa.finishAddingAuthenticator(additional.id, "Backup")).value
        val renamed = added.copy(name = "Tablet")
        assertEquals(Either.Right(renamed), mfa.renameAuthenticator(added.id, renamed.name))
        assertEquals(enabled.authenticatorApps + renamed, assertIs<MFAOverview.Enabled>(overview.value).authenticatorApps)
        assertEquals(Either.Right(Unit), mfa.removeAuthenticator(added.id, "password", "111111"))
        assertEquals(enabled, overview.value)

        assertEquals(Either.Right(Unit), mfa.confirmPassword("password"))
        val regenerated = assertIs<Either.Right<List<String>>>(mfa.generateRecoveryCodes()).value
        assertNotEquals(recoveryCodes, regenerated)
        assertEquals(Either.Right(Unit), mfa.turnOff("password", "111111"))
        assertEquals(MFAOverview.Disabled, overview.value)
        assertEquals(Either.Right(Unit), mfa.refreshOverview())
        assertEquals(MFAOverview.Disabled, overview.value)
    }

    @Test
    fun recreatingFeatureConsumersKeepsAccountDataAndSeparateAccountsStayIndependent() = runTest {
        val primary = AuthenticatorApp("primary", "Primary", LocalDate(2026, 10, 14))
        val repository = InMemoryMFARepository(MFAOverview.Enabled(listOf(primary), RecoveryCodesStatus(3, 10)))
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
}
