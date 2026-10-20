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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.client.ClearNewClientsForUserUseCase
import com.wire.kalium.logic.feature.client.ObserveNewClientsUseCase
import com.wire.kalium.logic.feature.server.ServerConfigForAccountUseCase
import com.wire.kalium.logic.feature.session.DeleteSessionUseCase
import com.wire.kalium.logic.feature.session.DoesValidNomadAccountExistUseCase
import com.wire.kalium.logic.feature.session.DoesValidSessionExistUseCase
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase

/** Individual bindings preserve their existing owner; this API does not create a consumer. */
public interface KaliumGlobalAccountsApi {
    public val globalDeleteSession: DeleteSessionUseCase
    public val globalServerConfigForAccounts: ServerConfigForAccountUseCase
    public val globalAddAuthenticatedAccount: AddAuthenticatedUserUseCase
    public val globalObserveValidAccounts: ObserveValidAccountsUseCase
    public val globalDoesValidSessionExist: DoesValidSessionExistUseCase
    public val globalDoesValidNomadAccountExist: DoesValidNomadAccountExistUseCase
    public val globalObserveNewClientsUseCase: ObserveNewClientsUseCase
    public val globalClearNewClientsForUser: ClearNewClientsForUserUseCase
}
