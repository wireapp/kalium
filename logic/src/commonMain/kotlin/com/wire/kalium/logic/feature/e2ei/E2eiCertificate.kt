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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class E2eiCertificate(
<<<<<<< HEAD
=======
    @SerialName("userHandle")
    var userHandle: String,
    @SerialName("status")
>>>>>>> 0cdf629aac (fix(e2ei): crash on downloading certificates (WPB-9097) 🍒 (#2761))
    val status: CertificateStatus,
    @SerialName("serialNumber")
    val serialNumber: String,
    @SerialName("certificateDetail")
    val certificateDetail: String,
<<<<<<< HEAD
=======
    @SerialName("thumbprint")
    val thumbprint: String,
    @SerialName("endAt")
>>>>>>> 0cdf629aac (fix(e2ei): crash on downloading certificates (WPB-9097) 🍒 (#2761))
    val endAt: Instant
) {
    companion object {
        fun fromWireIdentity(identity: WireIdentity, certificateStatusMapper: CertificateStatusMapper): E2eiCertificate? =
            identity.certificate?.let {
                E2eiCertificate(
                    status = certificateStatusMapper.toCertificateStatus(identity.status),
                    serialNumber = it.serialNumber,
                    certificateDetail = it.certificate,
                    endAt = Instant.fromEpochSeconds(it.endTimestampSeconds)
                )
            }
    }
}
