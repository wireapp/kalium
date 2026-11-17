/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.mfa

import com.wire.kalium.logic.feature.mfa.MFAEnrollment
import kotlin.uuid.Uuid

internal object DummyMFAData {
    const val OPERATION_DELAY_MILLIS = 500L
    const val TOTP_CODE = "111111"
    private const val RECOVERY_CODE_COUNT = 10
    private const val RECOVERY_CODE_LENGTH = 15
    private const val RECOVERY_CODE_GROUP_LENGTH = 5

    val initialRecoveryCodes = listOf(
        "k7qm2-xpd9f-hwtr4", "b3np8-vz6x2-kem5q", "a7tdr-jw9hp-q4c6y", "hnm8v-s3bkf-e5gaw", "z2rh6-uyc9p-mr3zx",
        "t7ldq-4fn2k-w8bpe", "c6vyj-3hm7a-r9xts", "g5kd2-ne8wq-u4pzm", "y3fb7-lc6rv-h2jdk", "s9tx4-mw5ge-a8qnb",
    )

    fun enrollment(): MFAEnrollment = MFAEnrollment(
        id = Uuid.random().toString(),
        account = "demo@wire.example",
        setupKey = "JBSWY3DPEHPK3PXP",
    )

    fun recoveryCodes(): List<String> = List(RECOVERY_CODE_COUNT) {
        Uuid.random().toString().replace("-", "").take(RECOVERY_CODE_LENGTH)
            .chunked(RECOVERY_CODE_GROUP_LENGTH).joinToString("-")
    }
}
