package com.wire.kalium.network.api.base.authenticated.keypackage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeyPackageList(
    @SerialName("key_packages")
    val keyPackages: List<KeyPackage>
)

@Serializable
data class ClaimedKeyPackageList(
    @SerialName("key_packages")
    val keyPackages: List<KeyPackageDTO>
)

@Serializable
data class KeyPackageDTO(
    @SerialName("client")
    val clientID: String,
    @SerialName("domain")
    val domain: String,
    @SerialName("key_package")
    val keyPackage: KeyPackage,
    @SerialName("key_package_ref")
    val keyPackageRef: KeyPackageRef,
    @SerialName("user")
    val userId: String
)

@Serializable
data class KeyPackageCountDTO(
    @SerialName("count") val count: Int
)
