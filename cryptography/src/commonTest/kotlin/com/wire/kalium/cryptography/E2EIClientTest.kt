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
package com.wire.kalium.cryptography

import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@IgnoreJS
@IgnoreIOS
class E2EIClientTest : BaseMLSClientTest() {
    data class SampleUser(
        val id: CryptoQualifiedID, val clientId: CryptoClientId, val name: String, val handle: String, val teamId: String? = ""
    ) {
        val qualifiedClientId: CryptoQualifiedClientId = CryptoQualifiedClientId(clientId.value, id)
    }

    private suspend fun createE2EIClient(user: SampleUser): E2EIClient {
        return createMLSClient(
            user.qualifiedClientId,
            ALLOWED_CIPHER_SUITES,
            DEFAULT_CIPHER_SUITE,
            mlsTransporter,
            epochObserver,
            TestScope()
        )
            .transaction {
                it.e2eiNewActivationEnrollment(user.name, user.handle, user.teamId, 90.days)
            }
    }

    @Test
    fun givenClient_whenPassingAcmeDirectoryResponse_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        val expectedDirectory = AcmeDirectory(
            "https://acme.elna.wire.link/acme/keycloakteams/new-nonce",
            "https://acme.elna.wire.link/acme/keycloakteams/new-account",
            "https://acme.elna.wire.link/acme/keycloakteams/new-order"
        )
        val directory = e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        assertEquals(expectedDirectory, directory)
    }

    @Test
    fun givenClient_whenCallingGetNewAccountRequest_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        assertTrue(e2eiClient.getNewAccountRequest(NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingGetNewOrderRequest_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        assertTrue(e2eiClient.getNewOrderRequest(NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingGetNewAuthzRequest_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        assertTrue(e2eiClient.getNewAuthzRequest(AUTHZ_URL1, NONCE).isNotEmpty())
        assertTrue(e2eiClient.getNewAuthzRequest(AUTHZ_URL2, NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingCreateDpopToken_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.setAuthzResponse(DPOP_AUTHZ_API_RESPONSE)
        assertTrue(e2eiClient.createDpopToken(NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingGetNewDpopChallengeRequest_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.setAuthzResponse(DPOP_AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        assertTrue(e2eiClient.getNewDpopChallengeRequest(ACCESS_TOKEN_RESPONSE, NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingGetNewOidcChallengeRequest_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.setAuthzResponse(DPOP_AUTHZ_API_RESPONSE)
        e2eiClient.setAuthzResponse(OIDC_AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        assertTrue(e2eiClient.getNewOidcChallengeRequest(OAUTH_ID_TOKEN, NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingCheckOrderRequest_ReturnNonEmptyResult() = runTest {
        val coreCryptoCentral = createCoreCrypto(ALICE1.qualifiedClientId)
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.getNewAuthzRequest(AUTHZ_URL1, NONCE)
        e2eiClient.getNewAuthzRequest(AUTHZ_URL2, NONCE)
        e2eiClient.setAuthzResponse(OIDC_AUTHZ_API_RESPONSE)
        e2eiClient.setAuthzResponse(DPOP_AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        e2eiClient.setDPoPChallengeResponse(DPOP_CHALLENGE_RESPONSE)
        e2eiClient.getNewOidcChallengeRequest(OAUTH_ID_TOKEN, NONCE)
        e2eiClient.setOIDCChallengeResponse(coreCryptoCentral, OIDC_CHALLENGE_RESPONSE)
        assertTrue(e2eiClient.checkOrderRequest(FINALIZE_ORDER_URL, NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingFinalizeRequest_ReturnNonEmptyResult() = runTest {
        val coreCryptoCentral = createCoreCrypto(ALICE1.qualifiedClientId)
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.getNewAuthzRequest(AUTHZ_URL1, NONCE)
        e2eiClient.getNewAuthzRequest(AUTHZ_URL2, NONCE)
        e2eiClient.setAuthzResponse(OIDC_AUTHZ_API_RESPONSE)
        e2eiClient.setAuthzResponse(DPOP_AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        e2eiClient.setDPoPChallengeResponse(DPOP_CHALLENGE_RESPONSE)
        e2eiClient.getNewOidcChallengeRequest(OAUTH_ID_TOKEN, NONCE)
        e2eiClient.setOIDCChallengeResponse(coreCryptoCentral, OIDC_CHALLENGE_RESPONSE)
        e2eiClient.checkOrderResponse(ORDER_RESPONSE)
        assertTrue(e2eiClient.finalizeRequest(NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingCertificateRequest_ReturnNonEmptyResult() = runTest {
        val coreCryptoCentral = createCoreCrypto(ALICE1.qualifiedClientId)
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.getNewAuthzRequest(AUTHZ_URL1, NONCE)
        e2eiClient.getNewAuthzRequest(AUTHZ_URL2, NONCE)
        e2eiClient.setAuthzResponse(OIDC_AUTHZ_API_RESPONSE)
        e2eiClient.setAuthzResponse(DPOP_AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        e2eiClient.setDPoPChallengeResponse(DPOP_CHALLENGE_RESPONSE)
        e2eiClient.getNewOidcChallengeRequest(OAUTH_ID_TOKEN, NONCE)
        e2eiClient.setOIDCChallengeResponse(coreCryptoCentral, OIDC_CHALLENGE_RESPONSE)
        e2eiClient.checkOrderResponse(ORDER_RESPONSE)
        e2eiClient.finalizeResponse(FINALIZE_RESPONSE)
        assertTrue(e2eiClient.certificateRequest(NONCE).isNotEmpty())
    }

    companion object {

        val DEFAULT_CIPHER_SUITE = MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val ALLOWED_CIPHER_SUITES = listOf(MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
        val ALICE1 = SampleUser(
            CryptoQualifiedID("837655f7-b448-465a-b4b2-93f0919b38f0", "elna.wire.link"),
            CryptoClientId("fb4b58152e20"),
            "Mojtaba Chenani",
            "mojtaba_wire",
            "team"
        )

        val ACME_DIRECTORY_API_RESPONSE = """
            {
              "newNonce": "https://acme.elna.wire.link/acme/keycloakteams/new-nonce",
              "newAccount": "https://acme.elna.wire.link/acme/keycloakteams/new-account",
              "newOrder": "https://acme.elna.wire.link/acme/keycloakteams/new-order",
              "revokeCert": "https://acme.elna.wire.link/acme/keycloakteams/revoke-cert",
              "keyChange": "https://acme.elna.wire.link/acme/keycloakteams/key-change"
            }
            """.toByteArray()

        val NONCE = "TGR6Rk45RlR2WDlzanMxWEpYd21YaFR0SkZBYTNzUWk"

        val NEW_ACCOUNT_API_RESPONSE = """
            {
               "contact": ["anonymous@anonymous.invalid"],
                "status": "valid",
                "orders": "https://acme.elna.wire.link/acme/keycloakteams/account/9ftonrYafcLPjIGsFyABdgnen9TLrBpv/orders"
            }""".toByteArray()

        val AUTHZ_URL1 = "https://acme.elna.wire.link/acme/keycloakteams/authz/1gpp07FUGPh6bFhnAZTuhhPIoGAx2xpw"
        val AUTHZ_URL2 = "https://acme.elna.wire.link/acme/keycloakteams/authz/mGCAn2FaKAVlO7n2MXdCaRjRsSwEHrel"

        val FINALIZE_ORDER_URL = "https://acme.elna.wire.link/acme/keycloakteams/order/c9mGRDNE7YRVRbk6jokwXNXPgU1n37iS/finalize"

        val NEW_ORDER_API_RESPONSE = """
            {
              "id": "c9mGRDNE7YRVRbk6jokwXNXPgU1n37iS",
              "status": "pending",
              "expires": "3000-04-04T09:42:17Z",
              "identifiers": [
                {
                  "type": "wireapp-device",
                  "value": "{\"client-id\":\"wireapp://F13jFZSpR0eZwUwOVfy89A!a07b91b6fc50a19a@elna.wire.link\",\"handle\":\"wireapp://%40miller80894@elna.wire.link\",\"name\":\"Brigette Miller\",\"domain\":\"elna.wire.link\"}"
                },
                {
                  "type": "wireapp-user",
                  "value": "{\"handle\":\"wireapp://%40miller80894@elna.wire.link\",\"name\":\"Brigette Miller\",\"domain\":\"elna.wire.link\"}"
                }
              ],
              "notBefore":"2023-05-07T12:00:50.1666Z",
              "notAfter":"3000-08-05T12:00:50.1666Z",
              "authorizations": [
                "$AUTHZ_URL1",
                "$AUTHZ_URL2"
              ],
              "finalize": "$FINALIZE_ORDER_URL"
            }
        """.toByteArray()

        val DPOP_AUTHZ_API_RESPONSE = """
            {
              "identifier": {
                "type": "wireapp-device",
                "value": "{\"client-id\":\"wireapp://F13jFZSpR0eZwUwOVfy89A!a07b91b6fc50a19a@elna.wire.link\",\"handle\":\"wireapp://%40miller80894@elna.wire.link\",\"name\":\"Brigette Miller\",\"domain\":\"elna.wire.link\"}"
              },
              "status": "pending",
              "challenges": [
                {
                  "type": "wire-dpop-01",
                  "status": "pending",
                  "token": "jA1p6uxBXOd7jguZMRDl5f26IO5s4JCA",
                  "url": "https://acme.elna.wire.link/acme/keycloakteams/challenge/1gpp07FUGPh6bFhnAZTuhhPIoGAx2xpw/33vBSFmrKO9SEjLM5p8y2Wzc7tXvsbd6",
                  "target": "https://elna.wire.link/clients/a07b91b6fc50a19a/access-token"
                }
              ],
              "wildcard": false,
              "expires": "3000-04-04T09:42:17Z"
            }""".toByteArray()
        val OIDC_AUTHZ_API_RESPONSE = """
            {
              "identifier": {
                "type": "wireapp-user",
                "value": "{\"handle\":\"wireapp://%40miller80894@elna.wire.link\",\"name\":\"Brigette Miller\",\"domain\":\"elna.wire.link\"}"
              },
              "status": "pending",
              "challenges": [
                {
                  "type": "wire-oidc-01",
                  "status": "pending",
                  "token": "m9kZjmdnBBbqeUAEIdLsPMYhczn7k5HY",
                  "url": "https://acme.elna.wire.link/acme/keycloakteams/challenge/mGCAn2FaKAVlO7n2MXdCaRjRsSwEHrel/I7RRkAbZ3ZiqLbpPd0dgLqHFMy0IY1t2",
                  "target": "https://keycloak.bund-next.wire.link/auth/realms/master?client_id=wireapp"
                }
              ],
              "wildcard": false,
              "expires": "3000-04-04T09:42:17Z"
            }
            """.toByteArray()

        val ACCESS_TOKEN_RESPONSE = """
            {
                "expires_in":"300", 
                "token":"eyJhbGciOiJFZERTQSIsInR5cCI6ImF0K2p3dCIsImp3ayI6eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6ImdxTk8wZ1FzRndfUUNQNm5xeV9BUWxNVDFQSTAtZ3lRMWZTMGhJZklyTWcifX0.eyJpYXQiOjE2ODM0NjgwODIsImV4cCI6MTY5MTI0NDA4MiwibmJmIjoxNjgzNDY4MDgyLCJpc3MiOiJodHRwczovL3N0YWdpbmcuemluZnJhLmlvL2NsaWVudHMvNGVlZGJmZTE2ZDI1YmJmMy9hY2Nlc3MtdG9rZW4iLCJzdWIiOiJpbTp3aXJlYXBwPVpURTFNamMwTXpFeU5EUTBOR0poWTJFMU5XWm1OakEyWlRrMU1qSXlNek0vNGVlZGJmZTE2ZDI1YmJmM0BzdGFnaW5nLnppbmZyYS5pbyIsImF1ZCI6Imh0dHBzOi8vc3RhZ2luZy56aW5mcmEuaW8vY2xpZW50cy80ZWVkYmZlMTZkMjViYmYzL2FjY2Vzcy10b2tlbiIsImp0aSI6ImM3ZjRhODAxLTVhZTUtNDNlOC04ZGJiLWRiYjE1ZmEwODM1ZSIsIm5vbmNlIjoiaEJWWTdjRjNSWC1lSnF0cW9nbVl0dyIsImNoYWwiOiJMeGpJNVBUeVZ2UU56ZW9yUWNPUm44OURtR1BaZEc3SyIsImNuZiI6eyJraWQiOiI3YXR6MldkcGxwSzNhbmtKVmp6cm1telVEZmdhNkFTMjRCRm1VbEJ2V1lFIn0sInByb29mIjoiZXlKaGJHY2lPaUpGWkVSVFFTSXNJblI1Y0NJNkltUndiM0FyYW5kMElpd2lhbmRySWpwN0ltdDBlU0k2SWs5TFVDSXNJbU55ZGlJNklrVmtNalUxTVRraUxDSjRJam9pVVZkUldrTklRemxSVFhOaVFtWk5SbmRrTmpONlZtNTFVbFZNVVVKSU1sVnNaRGh5WDBWTFNtSXlTU0o5ZlEuZXlKcFlYUWlPakUyT0RNME5qZ3dOemtzSW1WNGNDSTZNVFk0TXpRMk9ERXdPU3dpYm1KbUlqb3hOamd6TkRZNE1EYzVMQ0p6ZFdJaU9pSnBiVHAzYVhKbFlYQndQVnBVUlRGTmFtTXdUWHBGZVU1RVVUQk9SMHBvV1RKRk1VNVhXbTFPYWtFeVdsUnJNVTFxU1hsTmVrMHZOR1ZsWkdKbVpURTJaREkxWW1KbU0wQnpkR0ZuYVc1bkxucHBibVp5WVM1cGJ5SXNJbXAwYVNJNkltSmlOek5qTVdJekxUZ3dNMlF0TkRVMFlTMDROakl5TFdNellqUXlORFpoTTJZME9TSXNJbTV2Ym1ObElqb2lhRUpXV1RkalJqTlNXQzFsU25GMGNXOW5iVmwwZHlJc0ltaDBiU0k2SWxCUFUxUWlMQ0pvZEhVaU9pSm9kSFJ3Y3pvdkwzTjBZV2RwYm1jdWVtbHVabkpoTG1sdkwyTnNhV1Z1ZEhNdk5HVmxaR0ptWlRFMlpESTFZbUptTXk5aFkyTmxjM010ZEc5clpXNGlMQ0pqYUdGc0lqb2lUSGhxU1RWUVZIbFdkbEZPZW1WdmNsRmpUMUp1T0RsRWJVZFFXbVJITjBzaWZRLkl1YjJqTkRXY1lKdTZ0V1liX181UlNSSEhQQWV1ZmwwRkRPQzc3STY4UDZtcG96QjMxeGtmUEZUb2p3ckJtSEhLZHFLOWdJTTQ5YWcxb2pTclNlZkNnIiwiY2xpZW50X2lkIjoiaW06d2lyZWFwcD1aVEUxTWpjME16RXlORFEwTkdKaFkyRTFOV1ptTmpBMlpUazFNakl5TXpNLzRlZWRiZmUxNmQyNWJiZjNAc3RhZ2luZy56aW5mcmEuaW8iLCJhcGlfdmVyc2lvbiI6Mywic2NvcGUiOiJ3aXJlX2NsaWVudF9pZCJ9.kFoRHJotAJQTgLXXrH4m9ySutFJb2cc4Raa1nbOyRxNTFZyQuwbJT1jGAlIbziQmVEIZ5vneOg0TqBAyEtw3BQ",
                "type":"DPoP"
            }"""

        val OAUTH_ID_TOKEN =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImM5YWZkYTM2ODJlYmYwOWViMzA1NWMxYzRiZDM5Yjc1MWZiZjgxOTUiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiIzMzg4ODgxNTMwNzItNGZlcDZ0bjZrMTZ0bWNiaGc0bnQ0bHI2NXB2M2F2Z2kuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiIzMzg4ODgxNTMwNzItNGZlcDZ0bjZrMTZ0bWNiaGc0bnQ0bHI2NXB2M2F2Z2kuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTU0OTM2MTQ1MjMzNjgyNjc2OTAiLCJoZCI6IndpcmUuY29tIiwiZW1haWwiOiJtb2p0YWJhLmNoZW5hbmlAd2lyZS5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6IkRtZDhJQXdnWmVKX1QtUjBpRlpseGciLCJub25jZSI6IjEta2FZb1hRODdadEVlRGpVYTVQSVEiLCJuYW1lIjoiTW9qdGFiYSBDaGVuYW5pIiwicGljdHVyZSI6Imh0dHBzOi8vbGgzLmdvb2dsZXVzZXJjb250ZW50LmNvbS9hL0FHTm15eGFhaWFKa1Y4VDNmMW91d0RWVjNQck52UFBaVUpIZGdnMlJ4N0s0PXM5Ni1jIiwiZ2l2ZW5fbmFtZSI6Ik1vanRhYmEiLCJmYW1pbHlfbmFtZSI6IkNoZW5hbmkiLCJsb2NhbGUiOiJlbiIsImlhdCI6MTY4MzQ4MjIyOSwiZXhwIjoxNjgzNDg1ODI5fQ.r0hh1CtVUXncdWHoXsfAvhf0VuWGDooSRQnNqq0GrzAbYVENGwg0dm8P10Cq_UmCjjh56nC5laMQUcBu-sKW9mRbdKnHwdXXregTSgelQJFoIlusb_3VyHcWDY8Yf9xyuyZbu3wcduL8IndTvy8Sq7mIzGKhsHnLIy1UgHbCGMrzfY2LYCi9Df1ADqA8romigo8fdEAVUi9TAIC8SgHOcqLJt8mxlhKSPPwkJw5yZ3CRvF2NMNsVkYpE9hVYbHcZd6EAmJnljKPJ-NQLXUdjaU3ail80YQko4rcgF2QMZ3LBSMGJpI5LM2UhDxnktqBTpE2nLdwCFg64INS48DPDXQ"

        val DPOP_CHALLENGE_RESPONSE = """
            {
              "type": "wire-dpop-01",
              "status": "valid",
              "token": "nJMkyBgGovLoMFCK3AvxEZg3IDPnHaaB",
              "validated": "2024-04-03T11:09:49Z",
              "url": "https://acme.elna.wire.link/acme/keycloakteams/challenge/lHJZD35qsabL3O6zmnhphK0G3NBhmJir/jIfnktV1nSrDdJ5zuBoUnH90V65hfuCA",
              "target": "https://elna.wire.link/clients/a07b91b6fc50a19a/access-token"
            }""".toByteArray()

        val OIDC_CHALLENGE_RESPONSE = """
            {
              "type": "wire-oidc-01",
              "status": "valid",
              "token": "p7GkU6IOPQWtWwKIk56CenepIR3y4bMj",
              "validated": "2024-04-03T11:09:49Z",
              "url": "https://acme.elna.wire.link/acme/keycloakteams/challenge/YsZCZdrVlh56Icla8k8PJuPhD3MZsogs/JghpemWlBCI6TKRcQNfWQKpsoDlZDWDz",
              "target": "https://keycloak.bund-next.wire.link/auth/realms/master?client_id=wireapp"
            }""".toByteArray()

        val ORDER_RESPONSE = """
            {
              "id": "goywLpfyiGbt0ZrQ4bQyklwG70RrWIYi",
              "status": "ready",
              "expires": "3000-04-04T11:09:26Z",
              "identifiers": [
                {
                  "type": "wireapp-device",
                  "value": "{\"client-id\":\"wireapp://F13jFZSpR0eZwUwOVfy89A!a07b91b6fc50a19a@elna.wire.link\",\"handle\":\"wireapp://%40miller80894@elna.wire.link\",\"name\":\"Brigette Miller\",\"domain\":\"elna.wire.link\"}"
                },
                {
                  "type": "wireapp-user",
                  "value": "{\"handle\":\"wireapp://%40miller80894@elna.wire.link\",\"name\":\"Brigette Miller\",\"domain\":\"elna.wire.link\"}"
                }
              ],
              "notBefore": "2024-04-03T11:09:28.536894572Z",
              "notAfter": "3000-07-02T11:09:28.536894572Z",
              "authorizations": [
                "https://acme.elna.wire.link/acme/keycloakteams/authz/lHJZD35qsabL3O6zmnhphK0G3NBhmJir",
                "https://acme.elna.wire.link/acme/keycloakteams/authz/YsZCZdrVlh56Icla8k8PJuPhD3MZsogs"
              ],
              "finalize": "https://acme.elna.wire.link/acme/keycloakteams/order/goywLpfyiGbt0ZrQ4bQyklwG70RrWIYi/finalize"
            }""".toByteArray()
        val FINALIZE_RESPONSE = """
            {
              "id": "goywLpfyiGbt0ZrQ4bQyklwG70RrWIYi",
              "status": "valid",
              "expires": "3000-04-04T11:09:26Z",
              "identifiers": [
                {
                  "type": "wireapp-device",
                  "value": "{\"client-id\":\"wireapp://F13jFZSpR0eZwUwOVfy89A!a07b91b6fc50a19a@elna.wire.link\",\"handle\":\"wireapp://%40miller80894@elna.wire.link\",\"name\":\"Brigette Miller\",\"domain\":\"elna.wire.link\"}"
                },
                {
                  "type": "wireapp-user",
                  "value": "{\"handle\":\"wireapp://%40miller80894@elna.wire.link\",\"name\":\"Brigette Miller\",\"domain\":\"elna.wire.link\"}"
                }
              ],
              "notBefore": "2024-04-03T11:09:28.536894572Z",
              "notAfter": "3000-07-02T11:09:28.536894572Z",
              "authorizations": [
                "https://acme.elna.wire.link/acme/keycloakteams/authz/lHJZD35qsabL3O6zmnhphK0G3NBhmJir",
                "https://acme.elna.wire.link/acme/keycloakteams/authz/YsZCZdrVlh56Icla8k8PJuPhD3MZsogs"
              ],
              "finalize": "https://acme.elna.wire.link/acme/keycloakteams/order/goywLpfyiGbt0ZrQ4bQyklwG70RrWIYi/finalize",
              "certificate": "https://acme.elna.wire.link/acme/keycloakteams/certificate/cMFLY1InYUGVfOdrlgx9zoAIvipW6ocf"
            }""".toByteArray()
    }

    private val mlsTransporter = object : MLSTransporter {
        override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse {
            return MlsTransportResponse.Success
        }

        override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse {
            return MlsTransportResponse.Success
        }
    }

    private val epochObserver = object : MLSEpochObserver {
        override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) {

        }
    }
}
