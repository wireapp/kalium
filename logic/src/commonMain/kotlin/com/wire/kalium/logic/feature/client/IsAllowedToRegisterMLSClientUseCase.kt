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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.util.DelicateKaliumApi

/**
 * Answers the question if the self user allowed to register an MLS client
 *
 * Which we are allowed to do if:
 * - MLS support is enabled.
 * - MLS supported is configured on the backend, which can be verified by looking for the existence of MLS public keys.
 */
@DelicateKaliumApi("This use case performs network calls, consider using IsMLSEnabledUseCase.")
interface IsAllowedToRegisterMLSClientUseCase {
    suspend operator fun invoke(): Boolean
}

@OptIn(DelicateKaliumApi::class)
internal class IsAllowedToRegisterMLSClientUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
) : IsAllowedToRegisterMLSClientUseCase {

    override suspend operator fun invoke(): Boolean =
        featureSupport.isMLSSupported && mlsPublicKeysRepository.getKeys().isRight()
}
