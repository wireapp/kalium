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
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.kaliumLogger

/**
 * Use case to check if the CRL is expired and if so, register CRL and update conversation statuses if there is a change.
 */
internal interface RevocationListChecker {
    suspend fun check(url: String): Either<CoreFailure, ULong?>
}

internal class RevocationListCheckerImpl(
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val featureSupport: FeatureSupport,
    private val userConfigRepository: UserConfigRepository,
) : RevocationListChecker {
    private val logger = kaliumLogger.withTextTag("CheckRevocationListUseCase")
    override suspend fun check(url: String): Either<CoreFailure, ULong?> {
        val isE2EIEnabled = getIsE2EIEnabled()

        return if (isE2EIEnabled) {
            logger.i("checking crl url: $url")
            certificateRevocationListRepository.getClientDomainCRL(url).flatMap {
                currentClientIdProvider().flatMap { clientId ->
                    mlsClientProvider.getCoreCrypto(clientId).map { coreCrypto ->
                        logger.i("registering crl..")
                        coreCrypto.registerCrl(url, it).run {
                            this.expiration
                        }
                    }
                }
            }
        } else Either.Left(E2EIFailure.Disabled)
    }

    private fun getIsE2EIEnabled() = userConfigRepository.getE2EISettings().flatMap { settings ->
        userConfigRepository.isMLSEnabled()
            .map {
                it && settings.isRequired && featureSupport.isMLSSupported
            }
    }.getOrElse(false)
}
