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
package com.wire.kalium.logic.feature.e2ei

import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase

/** Check installed X.509 credentials, forcing a check even when E2EI is currently disabled. */
// todo(interface). extract interface for use case
public class CheckCrlRevocationListUseCase internal constructor(
    private val e2eiRepository: E2EIRepository,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
    kaliumLogger: KaliumLogger
) {

    private val logger = kaliumLogger.withTextTag("CheckCrlRevocationListUseCase")

    public suspend operator fun invoke(forceUpdate: Boolean) {
        logger.i("Checking certificate revocation list (CRL). Force update: $forceUpdate")
        if (!forceUpdate && !isE2EIEnabledUseCase()) return

        e2eiRepository.checkCredentials().onFailure {
            logger.w("Checking installed X.509 credentials failed: $it")
        }
    }
}
