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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.network.exceptions.KaliumException

/**
 * Whether receiving work that failed this way can succeed when its event is processed again.
 * The NSE's network rule, except federation that is refused or conflicting, which no retry resolves:
 * retrying those would fail the same batch on every sync restart.
 */
internal fun CoreFailure.canSucceedLater(): Boolean = when (this) {
    is NetworkFailure.FederatedBackendFailure.ConflictingBackends,
    is NetworkFailure.FederatedBackendFailure.FederationDenied,
    is NetworkFailure.FederatedBackendFailure.FederationNotEnabled,
    is NetworkFailure.FederatedBackendFailure.FederationNotImplemented -> false
    is NetworkFailure.NoNetworkConnection, MLSFailure.TransportUnavailable, is NetworkFailure.FederatedBackendFailure -> true
    is NetworkFailure.ServerMiscommunication -> kaliumException is KaliumException.ServerError
    else -> false
}
