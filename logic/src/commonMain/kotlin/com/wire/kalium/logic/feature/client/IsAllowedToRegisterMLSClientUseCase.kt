/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.DelicateKaliumApi

/**
 * Answers the question if the backend has MLS support enabled, is the self user allowed to register an MLS client?
 */
@DelicateKaliumApi("This use case performs network calls, consider using IsMLSEnabledUseCase.")
interface IsAllowedToRegisterMLSClientUseCase {
    suspend operator fun invoke(): Boolean
}

@OptIn(DelicateKaliumApi::class)
class IsAllowedToRegisterMLSClientUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val featureConfigRepository: FeatureConfigRepository,
    private val selfUserId: UserId
) : IsAllowedToRegisterMLSClientUseCase {

    override suspend operator fun invoke(): Boolean =
        featureConfigRepository.getFeatureConfigs().fold({
            false
        }, {
            featureSupport.isMLSSupported &&
                    it.mlsModel.status == Status.ENABLED &&
                    it.mlsModel.allowedUsers.contains(PlainId(selfUserId.value))
        })
}
