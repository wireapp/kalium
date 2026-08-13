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
package com.wire.kalium.logic.architecture

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The security providers debug screen reports which provider served each cryptographic call site, based on
 * what the call sites record when they run. A call site that does not record is invisible on that screen.
 *
 * This fails when a file resolves a JCA service without recording it, so new crypto code cannot silently
 * go unreported.
 */
class CryptoCallSiteRulesTest {

    @Test
    fun everyCryptoCallSiteRecordsWhichProviderServedIt() {
        val callSites = Konsist.scopeFromProduction()
            .files
            .filterNot { it.moduleName.startsWith(BUILD_TOOLING_MODULE_PREFIX) }
            // Only files that reach the JCA directly. Common code calling kalium's own SecureRandom
            // wrapper is covered by that wrapper's platform implementations instead.
            .filter { it.text.contains(JCA_PACKAGE) }
            .filter { it.text.contains(JCA_LOOKUP) }

        // Guards against a mis-scoped Konsist run silently passing because it found nothing at all.
        val found = callSites.map { it.nameWithExtension }.toSet()
        assertTrue(
            KNOWN_CRYPTO_CALL_SITES.all { it in found },
            "Konsist did not find the known crypto call sites ${KNOWN_CRYPTO_CALL_SITES - found}. " +
                    "The scope is probably wrong, so this test is not actually checking anything."
        )

        val notRecording = callSites
            .filterNot { it.text.contains(RECORDING) }
            .map { it.nameWithExtension }
            .toSet() - RECORDING_EXEMPT

        assertTrue(
            notRecording.isEmpty(),
            "$notRecording resolves a JCA service without calling recordCryptoService, so the provider that " +
                    "serves it will not appear on the security providers debug screen. Add a CryptoUsage " +
                    "entry, record the resolved instance, and probe it from probeCryptoServices()."
        )
    }

    private companion object {
        /** Gradle plugins hash build caches; that is not app crypto. */
        const val BUILD_TOOLING_MODULE_PREFIX = "plugins/"

        val JCA_PACKAGE = Regex("""java\.security\.|javax\.crypto\.""")

        /** A lookup that resolves against the installed security providers. */
        val JCA_LOOKUP = Regex(
            """SecureRandom\(\)|SecureRandom\.getInstanceStrong\(\)|""" +
                    """(Cipher|KeyGenerator|KeyPairGenerator|KeyStore|Mac|MessageDigest|Signature)\.getInstance\("""
        )

        val RECORDING = Regex("""recordCryptoService\(""")

        /** Files that resolve a service but deliberately do not record it. */
        val RECORDING_EXEMPT = setOf(
            // Only runs when certificate validation is disabled, so it is not a path the app takes.
            "HttpEngine.kt",
        )

        /**
         * Sanity check that the scope actually covers kalium's crypto modules, not just this one.
         *
         * - `AESUtils.kt` — asset IV, asset AES-256 key, asset cipher
         * - `SecureRandom.kt` — the strong secure random behind database secrets and random passwords
         */
        val KNOWN_CRYPTO_CALL_SITES = setOf(
            "AESUtils.kt",
            "SecureRandom.kt",
        )
    }
}
