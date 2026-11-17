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

import com.wire.kalium.logic.data.mfa.MFARepository

/** Account-scoped MFA settings capability, currently backed by an in-memory prototype. */
public class MFAScope internal constructor(repository: MFARepository) {
    public val observeOverview: ObserveMFAOverviewUseCase = ObserveMFAOverviewUseCaseImpl(repository)
    public val refreshOverview: RefreshMFAOverviewUseCase = RefreshMFAOverviewUseCaseImpl(repository)
    public val startSetup: StartMFASetupUseCase = StartMFASetupUseCaseImpl(repository)
    public val verifySetupCode: VerifyMFASetupCodeUseCase = VerifyMFASetupCodeUseCaseImpl(repository)
    public val completeSetup: CompleteMFASetupUseCase = CompleteMFASetupUseCaseImpl(repository)
    public val verifyAuthenticatorCode: VerifyAuthenticatorAppCodeUseCase = VerifyAuthenticatorAppCodeUseCaseImpl(repository)
    public val finishAddingAuthenticator: FinishAddingAuthenticatorAppUseCase = FinishAddingAuthenticatorAppUseCaseImpl(repository)
    public val renameAuthenticator: RenameAuthenticatorAppUseCase = RenameAuthenticatorAppUseCaseImpl(repository)
    public val removeAuthenticator: RemoveAuthenticatorAppUseCase = RemoveAuthenticatorAppUseCaseImpl(repository)
    public val confirmPassword: ConfirmMFAPasswordUseCase = ConfirmMFAPasswordUseCaseImpl(repository)
    public val generateRecoveryCodes: GenerateMFARecoveryCodesUseCase = GenerateMFARecoveryCodesUseCaseImpl(repository)
    public val turnOff: TurnOffMFAUseCase = TurnOffMFAUseCaseImpl(repository)
}
