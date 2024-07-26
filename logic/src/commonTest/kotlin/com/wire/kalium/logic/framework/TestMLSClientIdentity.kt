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
package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.feature.e2ei.Handle
import com.wire.kalium.logic.feature.e2ei.MLSClientE2EIStatus
import com.wire.kalium.logic.feature.e2ei.MLSClientIdentity
import com.wire.kalium.logic.feature.e2ei.MLSCredentialsType
import com.wire.kalium.logic.feature.e2ei.X509Identity
import kotlinx.datetime.Instant

object TestMLSClientIdentity {
    fun getMLSClientIdentityWithE2EI(clientId: QualifiedClientID, status: MLSClientE2EIStatus = MLSClientE2EIStatus.VALID) =
        MLSClientIdentity(
            clientId = clientId,
            status,
            thumbprint = "thumbprint",
            credentialType = MLSCredentialsType.X509,
            x509Identity = getValidX509Identity(clientId, status)
        )

    fun getMLSClientIdentityWithOutE2EI(clientId: QualifiedClientID) =
        MLSClientIdentity(
            clientId = clientId,
            MLSClientE2EIStatus.NOT_ACTIVATED,
            thumbprint = "thumbprint",
            credentialType = MLSCredentialsType.BASIC,
            x509Identity = null
        )

    fun getValidX509Identity(clientId: QualifiedClientID, status: MLSClientE2EIStatus) = X509Identity(
        Handle(
            scheme = "wireapp",
            handle = "user_handle",
            domain = clientId.userId.domain
        ),
        displayName = "User Test",
        domain = clientId.userId.domain,
        certificate = "certificate",
        serialNumber = "serialNumber",
        notBefore = Instant.DISTANT_PAST,
        notAfter = if (status == MLSClientE2EIStatus.EXPIRED) Instant.DISTANT_PAST else Instant.DISTANT_FUTURE
    )
}
