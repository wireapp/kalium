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

package com.wire.kalium.cryptography

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PkiTrustAnchorsTest {

    @Test
    fun givenCertificate_whenCalculatingFingerprint_thenHashesDerEncodedSpki() {
        val expected = EXPECTED_FIRST_SPKI_SHA256.hexToByteArray()

        val actual = pkiTrustAnchorFingerprint(FIRST_TRUST_ANCHOR)

        assertContentEquals(expected, actual)
    }

    @Test
    fun givenRotatedBundle_whenPlanningReconciliation_thenAddsNewBeforeRemovingOld() {
        val plan = planPkiTrustAnchorReconciliation(
            currentAnchors = listOf(FIRST_TRUST_ANCHOR),
            desiredPemBundle = SECOND_TRUST_ANCHOR
        )

        assertEquals(listOf(SECOND_TRUST_ANCHOR), plan.anchorsToAdd)
        assertEquals(1, plan.fingerprintsToRemove.size)
        assertContentEquals(EXPECTED_FIRST_SPKI_SHA256.hexToByteArray(), plan.fingerprintsToRemove.single())
    }

    @Test
    fun givenBundleContainingCurrentAndNewRoot_whenPlanningReconciliation_thenOnlyAddsNewRoot() {
        val plan = planPkiTrustAnchorReconciliation(
            currentAnchors = listOf(FIRST_TRUST_ANCHOR),
            desiredPemBundle = "$FIRST_TRUST_ANCHOR\n$SECOND_TRUST_ANCHOR"
        )

        assertEquals(listOf(SECOND_TRUST_ANCHOR), plan.anchorsToAdd)
        assertEquals(emptyList(), plan.fingerprintsToRemove)
    }

    @Test
    fun givenSameRootWithDifferentPemLineEndings_whenPlanningReconciliation_thenDoesNothing() {
        val plan = planPkiTrustAnchorReconciliation(
            currentAnchors = listOf(FIRST_TRUST_ANCHOR),
            desiredPemBundle = FIRST_TRUST_ANCHOR.replace("\n", "\r\n")
        )

        assertEquals(emptyList(), plan.anchorsToAdd)
        assertEquals(emptyList(), plan.fingerprintsToRemove)
    }

    @Test
    fun givenEmptyAuthoritativeBundle_whenPlanningReconciliation_thenFailsClosed() {
        assertFailsWith<IllegalArgumentException> {
            planPkiTrustAnchorReconciliation(
                currentAnchors = listOf(FIRST_TRUST_ANCHOR),
                desiredPemBundle = " \n\t"
            )
        }
    }

    private companion object {
        // From Core Crypto v10.4's PKI environment tests. The expected digest was independently
        // calculated with: openssl x509 -pubkey | openssl pkey -pubin -outform DER | openssl sha256
        const val FIRST_TRUST_ANCHOR = """-----BEGIN CERTIFICATE-----
MIIBkzCCAUWgAwIBAgIUHFYIFRkm33GKIOb4xLeNtkjl3TIwBQYDK2VwMDcxFTAT
BgNVBAMMDFRlc3QgUm9vdCBDQTERMA8GA1UECgwIVGVzdCBPcmcxCzAJBgNVBAYT
AlVTMB4XDTI2MDUyODE1MzA0NFoXDTM2MDUyNTE1MzA0NFowNzEVMBMGA1UEAwwM
VGVzdCBSb290IENBMREwDwYDVQQKDAhUZXN0IE9yZzELMAkGA1UEBhMCVVMwKjAF
BgMrZXADIQDa0nMgIgBZeNM2ysNUVp80zwjZNqPJt7HYK3GX7GPp9aNjMGEwHQYD
VR0OBBYEFHA0MmaaNGOTuBvdo3zzQoKFJ3p5MB8GA1UdIwQYMBaAFHA0MmaaNGOT
uBvdo3zzQoKFJ3p5MA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAUG
AytlcANBAJffPzL50OWnmEBo9mGBQfPVzKRIfFc8EaXox1D5VF9cC1r8nRa0hUq+
LOVS/gxNk618+PKA2bYq67MZQXCYGgk=
-----END CERTIFICATE-----"""

        const val SECOND_TRUST_ANCHOR = """-----BEGIN CERTIFICATE-----
MIIBfTCCAS+gAwIBAgIUOl/otqM7pBrpk59PsDIGFmXv1rUwBQYDK2VwMDQxHjAc
BgNVBAMMFUthbGl1bSBUcnVzdCBBbmNob3IgMjESMBAGA1UECgwJV2lyZSBUZXN0
MB4XDTI2MDgyMzE1MzMyNVoXDTM2MDgyMDE1MzMyNVowNDEeMBwGA1UEAwwVS2Fs
aXVtIFRydXN0IEFuY2hvciAyMRIwEAYDVQQKDAlXaXJlIFRlc3QwKjAFBgMrZXAD
IQAKSGiM5AnUQUv6q/AtMbwXZ06s4aAwDXOoFaJbTmFJQqNTMFEwHQYDVR0OBBYE
FJH7SBnNlMEGneJxpITt9mpGrrHnMB8GA1UdIwQYMBaAFJH7SBnNlMEGneJxpITt
9mpGrrHnMA8GA1UdEwEB/wQFMAMBAf8wBQYDK2VwA0EARQzenrwPqog9DtgcQiYn
byPUXy7sb/MOs+XT97KoIuAsaQorurP2z7DMBVT8Dy4QN1A5GHIPTfdKLVoNJt7k
Bw==
-----END CERTIFICATE-----"""

        const val EXPECTED_FIRST_SPKI_SHA256 = "0efc9faa274dd0e6db3665a391c8fca77ec71b5095377e3831e9e9b7b6f40bf4"
    }
}
