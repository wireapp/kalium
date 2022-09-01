package com.wire.kalium.cryptography

typealias MLSGroupId = String

data class CryptoClientId(val value: String) {
    override fun toString() = value
}

data class PlainUserId(val value: String) {
    override fun toString() = value
}

typealias CryptoUserID = CryptoQualifiedID

data class CryptoQualifiedID(
    val value: String,
    val domain: String
) {
    override fun toString() = "$value@$domain"
}

data class CryptoQualifiedClientId(
    val value: String,
    val userId: CryptoQualifiedID
) {
    override fun toString() = "${userId.value}:${value}@${userId.domain}"

    companion object {
        fun fromEncodedString(value: String): CryptoQualifiedClientId? {
            val components = value.split(":", "@")
            if (components.size != 3) return null

            return CryptoQualifiedClientId(
                components[0],
                CryptoQualifiedID(components[1], components[2])
            )
        }
    }
}
