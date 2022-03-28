package com.wire.kalium.cryptography

expect open class BaseMLSClientTest() {

    fun createMLSClient(clientId: CryptoQualifiedClientId): MLSClient

}
