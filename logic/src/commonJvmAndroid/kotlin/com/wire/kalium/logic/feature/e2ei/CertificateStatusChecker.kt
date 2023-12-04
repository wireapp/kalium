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

import com.wire.kalium.cryptography.CertificateStatus
import java.util.Date

actual interface CertificateStatusChecker {
    actual fun status(notAfterTimestamp: Long, deviceStatus: CertificateStatus): CertificateStatus
}

actual class CertificateStatusCheckerImpl : CertificateStatusChecker {
    override fun status(notAfterTimestamp: Long, deviceStatus: CertificateStatus): CertificateStatus {
        val current = Date()

        return when {
            (deviceStatus == CertificateStatus.REVOKED) -> CertificateStatus.REVOKED
            (current.time >= notAfterTimestamp || deviceStatus == CertificateStatus.EXPIRED) -> CertificateStatus.EXPIRED
            else -> CertificateStatus.VALID
        }
    }
}
