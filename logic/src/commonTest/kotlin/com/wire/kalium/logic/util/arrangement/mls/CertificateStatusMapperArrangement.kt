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
package com.wire.kalium.logic.util.arrangement.mls

import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.logic.feature.e2ei.CertificateStatus
import com.wire.kalium.logic.feature.e2ei.CertificateStatusMapper
import io.mockative.any
import io.mockative.every
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

interface CertificateStatusMapperArrangement {
    val certificateStatusMapper: CertificateStatusMapper

    fun withCertificateStatusMapperReturning(
        result: CertificateStatus,
        certificateMatcher: Matcher<CryptoCertificateStatus> = any()
    )
}

class CertificateStatusMapperArrangementImpl : CertificateStatusMapperArrangement {
    override val certificateStatusMapper: CertificateStatusMapper =
        mock(CertificateStatusMapper::class)

    override fun withCertificateStatusMapperReturning(
        result: CertificateStatus,
        certificateMatcher: Matcher<CryptoCertificateStatus>
    ) {
        every {
            certificateStatusMapper.toCertificateStatus(matches { certificateMatcher.matches(it) })
        }.returns(result)
    }
}
