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
import com.wire.kalium.logic.di.MapperProvider
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class E2eiCertificate(
    @SerialName("userHandle")
    var userHandle: String,
    @SerialName("status")
    val status: CertificateStatus,
    @SerialName("serialNumber")
    val serialNumber: String,
    @SerialName("certificateDetail")
    val certificateDetail: String,
    @SerialName("thumbprint")
    val thumbprint: String,
    @SerialName("endAt")
    val endAt: Instant
) {
    companion object {
        val certificateStatusMapper = MapperProvider.certificateStatusMapper()

        fun fromWireIdentity(identity: WireIdentity): E2eiCertificate? =
            identity.certificate?.let {
                E2eiCertificate(
                    userHandle = it.handle.handle,
                    status = certificateStatusMapper.toCertificateStatus(identity.status),
                    serialNumber = it.serialNumber,
                    certificateDetail = it.certificate,
                    thumbprint = it.thumbprint,
                    endAt = Instant.fromEpochSeconds(it.endTimestampSeconds)
                )
            }
    }
}
