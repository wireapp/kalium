package com.wire.kalium.testservice.models

data class InstanceRequest(
    val backend: String = "staging",
    val customBackend: CustomBackend? = null,
    val federationDomain: String? = null,
    val deviceClass: String? = null,
    val deviceLabel: String? = null,
    val deviceName: String? = null,
    val email: String = "",
    val isTemporary: Boolean? = false,
    val name: String = "",
    val password: String = ""
)

