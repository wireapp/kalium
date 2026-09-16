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

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PkiTrustAnchorAdditionIntegrationTest {

    @Test
    fun givenInstalledRoot_whenAddingExistingAndNewRoots_thenCoreCryptoKeepsBothRoots() = runTest {
        val root = Files.createTempDirectory("cc-pki-addition-test").toFile()
        val central = coreCryptoCentral(root.absolutePath, ByteArray(32))

        try {
            central.configurePkiEnvironment(UnexpectedPkiHooks)
            central.addPkiTrustAnchors(FIRST_TRUST_ANCHOR)
            central.addPkiTrustAnchors(FIRST_TRUST_ANCHOR)
            central.addPkiTrustAnchors(SECOND_TRUST_ANCHOR)

            val installedRoots = central.getPkiTrustAnchors()
            assertEquals(2, installedRoots.size)
            assertEquals(
                emptyList(),
                findPkiTrustAnchorsToAdd(
                    installedAnchors = installedRoots,
                    pemBundle = "$FIRST_TRUST_ANCHOR\n$SECOND_TRUST_ANCHOR"
                )
            )
        } finally {
            central.close()
            root.deleteRecursively()
        }
    }

    private object UnexpectedPkiHooks : PkiEnvironmentHooks {
        override suspend fun httpRequest(
            method: PkiHttpMethod,
            url: String,
            headers: List<PkiHttpHeader>,
            body: ByteArray
        ): PkiHttpResponse = error("No HTTP request is expected while adding roots")

        override suspend fun authenticate(
            idp: String,
            keyAuth: String,
            acmeAud: String
        ): String = error("No authentication is expected while adding roots")

        override suspend fun getBackendNonce(): String =
            error("No backend nonce is expected while adding roots")

        override suspend fun fetchBackendAccessToken(dpop: String): String =
            error("No backend access token is expected while adding roots")
    }

    private companion object {
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
    }
}
