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

/**
 * Account-scoped capability for managing MFA settings.
 *
 * **Warning:** Currently backed by a temporary dummy implementation.
 */
public class MFAScope internal constructor(private val repository: MFARepository) {
    public val observeOverview: ObserveMFAOverviewUseCase
        get() = ObserveMFAOverviewUseCaseImpl(repository)

    public val refreshOverview: RefreshMFAOverviewUseCase
        get() = RefreshMFAOverviewUseCaseImpl(repository)

    public val startSetup: StartMFASetupUseCase
        get() = StartMFASetupUseCaseImpl(repository)

    public val verifySetupCode: VerifyMFASetupCodeUseCase
        get() = VerifyMFASetupCodeUseCaseImpl(repository)

    public val completeSetup: CompleteMFASetupUseCase
        get() = CompleteMFASetupUseCaseImpl(repository)

    public val verifyAuthenticatorCode: VerifyAuthenticatorAppCodeUseCase
        get() = VerifyAuthenticatorAppCodeUseCaseImpl(repository)

    public val finishAddingAuthenticator: FinishAddingAuthenticatorAppUseCase
        get() = FinishAddingAuthenticatorAppUseCaseImpl(repository)

    public val renameAuthenticator: RenameAuthenticatorAppUseCase
        get() = RenameAuthenticatorAppUseCaseImpl(repository)

    public val removeAuthenticator: RemoveAuthenticatorAppUseCase
        get() = RemoveAuthenticatorAppUseCaseImpl(repository)

    public val confirmPassword: ConfirmMFAPasswordUseCase
        get() = ConfirmMFAPasswordUseCaseImpl(repository)

    public val generateRecoveryCodes: GenerateMFARecoveryCodesUseCase
        get() = GenerateMFARecoveryCodesUseCaseImpl(repository)

    public val turnOff: TurnOffMFAUseCase
        get() = TurnOffMFAUseCaseImpl(repository)
}
