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
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@Ignore
class E2EIClientTest : BaseMLSClientTest() {
    data class SampleUser(
        val id: CryptoQualifiedID, val clientId: CryptoClientId, val name: String, val handle: String, val teamId: String? = ""
    ) {
        val qualifiedClientId: CryptoQualifiedClientId = CryptoQualifiedClientId(clientId.value, id)
    }

    private suspend fun createE2EIClient(user: SampleUser): E2EIClient {
        return createMLSClient(user.qualifiedClientId).e2eiNewActivationEnrollment(
            user.name, user.handle, user.teamId,90.days
        )
    }

    @Test
    fun givenClient_whenPassingAcmeDirectoryResponse_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        val expectedDirectory = AcmeDirectory(
            "https://balderdash.hogwash.work:9000/acme/wire/new-nonce",
            "https://balderdash.hogwash.work:9000/acme/wire/new-account",
            "https://balderdash.hogwash.work:9000/acme/wire/new-order"
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
        assertTrue(e2eiClient.getNewAuthzRequest(AUTHZ_URL, NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingCreateDpopToken_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.setAuthzResponse(AUTHZ_API_RESPONSE)
        assertTrue(e2eiClient.createDpopToken(NONCE).isNotEmpty())
    }

    //todo: fix later
    @Ignore
    @Test
    fun givenClient_whenCallingGetNewDpopChallengeRequest_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.setAuthzResponse(AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        assertTrue(e2eiClient.getNewDpopChallengeRequest(ACCESS_TOKEN_RESPONSE, NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingGetNewOidcChallengeRequest_ReturnNonEmptyResult() = runTest {
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.setAuthzResponse(AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        assertTrue(e2eiClient.getNewOidcChallengeRequest(OAUTH_ID_TOKEN, REFRESH_TOKEN, NONCE).isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingCheckOrderRequest_ReturnNonEmptyResult() = runTest {
        val coreCryptoCentral = createCoreCrypto(ALICE1.qualifiedClientId)
        val e2eiClient = createE2EIClient(ALICE1)
        e2eiClient.directoryResponse(ACME_DIRECTORY_API_RESPONSE)
        e2eiClient.setAccountResponse(NEW_ACCOUNT_API_RESPONSE)
        e2eiClient.setOrderResponse(NEW_ORDER_API_RESPONSE)
        e2eiClient.setAuthzResponse(AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        e2eiClient.setDPoPChallengeResponse(DPOP_CHALLENGE_RESPONSE)
        e2eiClient.getNewOidcChallengeRequest(OAUTH_ID_TOKEN, REFRESH_TOKEN, NONCE)
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
        e2eiClient.setAuthzResponse(AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        e2eiClient.setDPoPChallengeResponse(DPOP_CHALLENGE_RESPONSE)
        e2eiClient.getNewOidcChallengeRequest(OAUTH_ID_TOKEN, REFRESH_TOKEN, NONCE)
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
        e2eiClient.setAuthzResponse(AUTHZ_API_RESPONSE)
        e2eiClient.createDpopToken(NONCE)
        e2eiClient.setDPoPChallengeResponse(DPOP_CHALLENGE_RESPONSE)
        e2eiClient.getNewOidcChallengeRequest(OAUTH_ID_TOKEN, REFRESH_TOKEN, NONCE)
        e2eiClient.setOIDCChallengeResponse(coreCryptoCentral, OIDC_CHALLENGE_RESPONSE)
        e2eiClient.checkOrderResponse(ORDER_RESPONSE)
        e2eiClient.finalizeResponse(FINALIZE_RESPONSE)
        assertTrue(e2eiClient.certificateRequest(NONCE).isNotEmpty())
    }

    companion object {

        val ALICE1 = SampleUser(
            CryptoQualifiedID("837655f7-b448-465a-b4b2-93f0919b38f0", "elna.wire.link"),
            CryptoClientId("fb4b58152e20"),
            "Mojtaba Chenani",
            "mojtaba_wire",
            "team"
        )

        val ACME_DIRECTORY_API_RESPONSE = """
            {
                "newNonce": "https://balderdash.hogwash.work:9000/acme/wire/new-nonce",
                "newAccount": "https://balderdash.hogwash.work:9000/acme/wire/new-account",
                "newOrder": "https://balderdash.hogwash.work:9000/acme/wire/new-order",
                "revokeCert": "https://balderdash.hogwash.work:9000/acme/wire/revoke-cert",
                "keyChange": "https://balderdash.hogwash.work:9000/acme/wire/key-change"
            }
            """.toByteArray()

        val NONCE = "TGR6Rk45RlR2WDlzanMxWEpYd21YaFR0SkZBYTNzUWk"
        val REFRESH_TOKEN = "YRjxLpsjRqL7zYuKstXogqioA_P3Z4fiEuga0NCVRcDSc8cy_9msxg"

        val NEW_ACCOUNT_API_RESPONSE = """
            {
               "contact":["unknown@example.com"],
               "status":"valid",
               "orders":"https://balderdash.hogwash.work:9000/acme/wire/account/9wXZb701i4JTE4ODOP5aMfsLJn7tpJ3B/orders"
            }""".toByteArray()

        val AUTHZ_URL = "https://balderdash.hogwash.work:9000/acme/wire/authz/CSGJUN9BQ0mhELCBbvBJI7AlxcAH9ypD"

        val FINALIZE_ORDER_URL = "https://balderdash.hogwash.work:9000/acme/wire/order/Q4LJjBnX7rA8dwxVreikzmVJBfCvrVs0/finalize"

        val NEW_ORDER_API_RESPONSE = """
            {
               "id":"Q4LJjBnX7rA8dwxVreikzmVJBfCvrVs0",
               "status":"pending",
               "expires":"3000-05-08T12:00:50Z",
               "identifiers":[
                  {
                     "type":"wireapp-id",
                     "value":"{\"name\":\"Mojtaba Chenani\",\"domain\":\"elna.wire.link\",\"client-id\":\"im:wireapp=IG9YvzuWQIKUaRk12F5CIQ/953218e68a63641f@elna.wire.link\",\"handle\":\"im:wireapp=mojtaba_wire\"}"
                  }
               ],
               "notBefore":"2023-05-07T12:00:50.1666Z",
               "notAfter":"3000-08-05T12:00:50.1666Z",
               "authorizations":[
                  "$AUTHZ_URL"
               ],
               "finalize":"$FINALIZE_ORDER_URL"
            }""".toByteArray()

        val AUTHZ_API_RESPONSE = """
            {
                "challenges": [
                    {
                        "status": "pending",
                        "target": "https://accounts.google.com",
                        "token": "b0xOVyQP7BXih0LkIEQRBCFjaHtmFpXR",
                        "type": "wire-oidc-01",
                        "url": "https://balderdash.hogwash.work:9000/acme/google-android/challenge/hHt8EBiiek0yxnVdjnA3WiF0ieDNQZmk/9IcPupsVVSR7vqalj1LqqZnGlAXFmmUI"
                    },
                    {
                        "status": "pending",
                        "target": "https://nginz-https.anta.wire.link/v4/clients/89f1c4056c99edcb/access-token",
                        "token": "b0xOVyQP7BXih0LkIEQRBCFjaHtmFpXR",
                        "type": "wire-dpop-01",
                        "url": "https://balderdash.hogwash.work:9000/acme/google-android/challenge/hHt8EBiiek0yxnVdjnA3WiF0ieDNQZmk/Z7WTwaKyyJf6icWRxLauZS9y5JuCtxUR"
                    }
                ],
                "expires": "3000-06-07T10:22:49Z",
                "identifier": {
                    "type": "wireapp-id",
                     "value":"{\"name\":\"Mojtaba Chenani\",\"domain\":\"elna.wire.link\",\"client-id\":\"im:wireapp=IG9YvzuWQIKUaRk12F5CIQ/953218e68a63641f@elna.wire.link\",\"handle\":\"im:wireapp=mojtaba_wire\"}"
                },
                "status": "pending",
                "wildcard": false
            }""".toByteArray()

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
              "type":"wire-dpop-01", 
              "url":"https://balderdash.hogwash.work:9000/acme/wire/challenge/iO6dShnf4v4ibIkpUMDYJWFh6ndSMjGx/fJLaKSLTGiztUJXz6YDJnxoJI0na26Fr", 
              "status":"valid", 
              "token":"LxjI5PTyVvQNzeorQcORn89DmGPZdG7K", 
              "nonce":"WWFsUjlkMm9rVWZJZE9FWHoxcmRqMUxPR3JtTFFVeG8"
            }""".toByteArray()

        val OIDC_CHALLENGE_RESPONSE = """
            {
                "type":"wire-oidc-01", 
                "url":"https://balderdash.hogwash.work:9000/acme/wire/challenge/iO6dShnf4v4ibIkpUMDYJWFh6ndSMjGx/mIgff1EJsncnHZfK98A6ARbsH69z8Dpp", 
                "status":"valid", "token":"LxjI5PTyVvQNzeorQcORn89DmGPZdG7K", 
                "nonce":"UW9YY2RraDNQOGZ0QUpYVUt5cDhwVVVSRUV1RDd3akw"
            }""".toByteArray()

        val ORDER_RESPONSE = """
            {
                "status": "ready",
                "finalize": "https://localhost:55170/acme/acme/order/FaKNEM5iL79ROLGJdO1DXVzIq5rxPEob/finalize",
                "identifiers": [
                    {
                        "type": "wireapp-id",
                     "value":"{\"name\":\"Mojtaba Chenani\",\"domain\":\"elna.wire.link\",\"client-id\":\"im:wireapp=IG9YvzuWQIKUaRk12F5CIQ/953218e68a63641f@elna.wire.link\",\"handle\":\"im:wireapp=mojtaba_wire\"}"
                    }
                ],
                "authorizations": [
                    "https://localhost:55170/acme/acme/authz/ZelRfonEK02jDGlPCJYHrY8tJKNsH0mw"
                ],
                "expires": "3000-02-10T14:59:20Z",
                "notBefore": "2013-02-09T14:59:20.442908Z",
                "notAfter": "3000-02-09T15:59:20.442908Z"
            }""".toByteArray()
        val FINALIZE_RESPONSE = """
            {
                "certificate": "https://localhost:55170/acme/acme/certificate/rLhCIYygqzWhUmP1i5tmtZxFUvJPFxSL",
                "status": "valid",
                "finalize": "https://localhost:55170/acme/acme/order/FaKNEM5iL79ROLGJdO1DXVzIq5rxPEob/finalize",
                "identifiers": [
                    {
                        "type": "wireapp-id",
                     "value":"{\"name\":\"Mojtaba Chenani\",\"domain\":\"elna.wire.link\",\"client-id\":\"im:wireapp=IG9YvzuWQIKUaRk12F5CIQ/953218e68a63641f@elna.wire.link\",\"handle\":\"im:wireapp=mojtaba_wire\"}"
                    }
                ],
                "authorizations": [
                    "https://localhost:55170/acme/acme/authz/ZelRfonEK02jDGlPCJYHrY8tJKNsH0mw"
                ],
                "expires": "3000-02-10T14:59:20Z",
                "notBefore": "2013-02-09T14:59:20.442908Z",
                "notAfter": "3000-02-09T15:59:20.442908Z"
            }""".toByteArray()
    }
}
