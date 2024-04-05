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

import com.wire.kalium.cryptography.WireIdentity
import kotlinx.datetime.Instant

data class E2eiCertificate(
    val status: CertificateStatus,
    val serialNumber: String,
    val certificateDetail: String,
    val endAt: Instant
) {
    companion object {
        fun fromWireIdentity(identity: WireIdentity, certificateStatusMapper: CertificateStatusMapper): E2eiCertificate =
            E2eiCertificate(
                status = certificateStatusMapper.toCertificateStatus(identity.status),
                serialNumber = identity.serialNumber,
                certificateDetail = identity.certificate,
                endAt = Instant.fromEpochSeconds(identity.endTimestampSeconds)
            )
    }
}
