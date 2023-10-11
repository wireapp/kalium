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
package com.wire.kalium.logic.feature.e2ei

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

actual fun getCertificateStatus(notAfterTimestamp: Long): CertificateStatus {
    // TODO check for revoked from coreCrypto when API is ready

    val current = Date()
    if (current.time >= notAfterTimestamp)
        return CertificateStatus.EXPIRED
    return CertificateStatus.VALID
}

actual fun decodePemCertificate(
    certificate: String
): E2eiCertificate {
    val certificateFactory = CertificateFactory.getInstance(TYPE)

    ByteArrayInputStream(certificate.toByteArray()).run {
        (certificateFactory.generateCertificate(this) as X509Certificate).also {
            return E2eiCertificate(
                issuer = it.issuerX500Principal.name,
                status = getCertificateStatus(it.notAfter.time),
                serialNumber = it.serialNumber.toString(BASE_16)
                    .chunked(CHUNK_SIZE)
                    .joinToString(SEPARATOR),
                certificateDetail = certificate
            )
        }
    }
}

private const val TYPE = "X.509"
private const val CHUNK_SIZE = 2
private const val SEPARATOR = ":"
private const val BASE_16 = 16
