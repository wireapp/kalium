package com.wire.kalium.testservice.models

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.annotations.ApiModelProperty

class InstanceRequest(
    @ApiModelProperty(notes = "Backend type", example = "staging", required = true)
    private val backend: String,
    private val customBackend: CustomBackend?,
    private val federationDomain: String, private val deviceClass: String?,
    private val deviceLabel: String?, private val deviceName: String?,
    private val email: String, private val isTemporary: Boolean?,
    private val name: String?, private val password: String
) {

    @JsonProperty
    fun getBackend(): String {
        return backend
    }

    @JsonProperty
    fun getCustomBackend(): CustomBackend? {
        return customBackend
    }

    @JsonProperty
    fun getFederationDomain(): String {
        return federationDomain
    }

    @JsonProperty
    fun getEmail(): String {
        return email
    }

    @JsonProperty
    fun getPassword(): String {
        return password
    }

    @JsonProperty
    fun isTemporary(): Boolean {
        return isTemporary ?: true
    }

    @JsonProperty
    fun getName(): String? {
        return name
    }

    @JsonProperty
    fun getDeviceClass(): String? {
        return deviceClass
    }

    @JsonProperty
    fun getDeviceLabel(): String? {
        return deviceLabel
    }

    @JsonProperty
    fun getDeviceName(): String? {
        return deviceName
    }

}
