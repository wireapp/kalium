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

import com.wire.kalium.logic.feature.connection.AcceptConnectionRequestUseCase
import com.wire.kalium.logic.feature.connection.BlockUserUseCase
import com.wire.kalium.logic.feature.connection.CancelConnectionRequestUseCase
import com.wire.kalium.logic.feature.connection.IgnoreConnectionRequestUseCase
import com.wire.kalium.logic.feature.connection.LegacyConnectionDependencies
import com.wire.kalium.logic.feature.connection.LegacyConnectionFacadeGraph
import com.wire.kalium.logic.feature.connection.SendConnectionRequestUseCase
import com.wire.kalium.logic.feature.connection.UnblockUserUseCase

/** Individual bindings preserve their existing owner; this API does not create a consumer. */
public interface KaliumConnectionApi {
    public val connectionSendConnectionRequest: SendConnectionRequestUseCase
    public val connectionCancelConnectionRequest: CancelConnectionRequestUseCase
    public val connectionIgnoreConnectionRequest: IgnoreConnectionRequestUseCase
    public val connectionAcceptConnectionRequest: AcceptConnectionRequestUseCase
    public val connectionBlockUser: BlockUserUseCase
    public val connectionUnblockUser: UnblockUserUseCase

    /** Every call creates a fresh graph with the original per-facade captures and caches. */
    public fun interface Factory {
        public fun create(): KaliumConnectionApi
    }
}

/** Same generated graph/capture boundary as the compatibility facade. */
internal fun createConnectionApi(
    factory: LegacyConnectionFacadeGraph.Factory,
    dependencies: LegacyConnectionDependencies,
): KaliumConnectionApi = factory.create(dependencies)
