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

import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.logic.util.serialNumber

actual interface PemCertificateDecoder {
    actual fun decode(certificate: String, status: CryptoCertificateStatus): E2eiCertificate
}

actual class PemCertificateDecoderImpl actual constructor(
    private val x509CertificateGenerator: X509CertificateGenerator,
    private val certificateStatusChecker: CertificateStatusChecker
) : PemCertificateDecoder {
    override fun decode(certificate: String, status: CryptoCertificateStatus): E2eiCertificate {
        x509CertificateGenerator.generate(certificate.toByteArray()).also {
            return E2eiCertificate(
                issuer = it.value.issuerX500Principal.name,
                status = certificateStatusChecker.status(it.value.notAfter.time, status),
                serialNumber = it.value.serialNumber.toString(BASE_16).serialNumber(),
                certificateDetail = certificate
            )
        }
    }
}

private const val BASE_16 = 16
