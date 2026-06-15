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

package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs

/**
 * Checks whether a deep-link initiated login targets a different backend than
 * the currently active session. When [KaliumConfigs.blockCrossBackendLogin] is
 * disabled, or when there is no valid current session, this always returns false.
 */
public class IsCrossBackendLoginBlockedUseCase internal constructor(
    private val kaliumConfigs: KaliumConfigs,
    private val currentSession: suspend () -> CurrentSessionResult,
    private val serverConfigForAccount: suspend (UserId) -> ServerConfigForAccountUseCase.Result,
) {
    public sealed interface Target {
        public data class Links(val links: ServerConfig.Links) : Target
        public data class SsoConfigId(val id: String) : Target
    }

    @Suppress("ReturnCount")
    public suspend operator fun invoke(target: Target): Boolean {
        if (!kaliumConfigs.blockCrossBackendLogin) return false
        val current = currentSessionConfig() ?: return false
        return when (target) {
            is Target.Links -> current.links != target.links
            is Target.SsoConfigId -> current.id != target.id
        }
    }

    @Suppress("ReturnCount")
    private suspend fun currentSessionConfig(): ServerConfig? {
        val session = currentSession() as? CurrentSessionResult.Success ?: return null
        val valid = session.accountInfo as? AccountInfo.Valid ?: return null
        val result = serverConfigForAccount(valid.userId) as? ServerConfigForAccountUseCase.Result.Success
        return result?.config
    }
}
