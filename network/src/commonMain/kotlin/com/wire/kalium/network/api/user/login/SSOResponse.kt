package com.wire.kalium.network.api.user.login


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SSOResponse(
    @SerialName("uri")
    val uri: String?,
    @SerialName("xml")
    val xml: AuthnRequestSSO?
)

@Serializable
data class AuthnRequestSSO(
    @SerialName("iD")
    val id: SSOID,
    @SerialName("issueInstant")
    val issueInstant: IssueInstant,
    @SerialName("issuer")
    val issuer: String,
    @SerialName("nameIDPolicy")
    val nameIDPolicy: NameIDPolicy?
)

@Serializable
data class IssueInstant(
    @SerialName("time")
    val time: String
)


@Serializable
data class SSOID(
    @SerialName("iD")
    val iD: XmlText
)

@Serializable
data class XmlText(
    @SerialName("fromXmlText")
    val fromXmlText: String
)

@Serializable
data class NameIDPolicy(
    @SerialName("allowCreate")
    val allowCreate: Boolean,
    @SerialName("format")
    val format: NameIDFormat,
    @SerialName("spNameQualifier")
    val spNameQualifier: XmlText?
)

@Serializable
enum class NameIDFormat {
    @SerialName("NameIDFUnspecified")
    NameIDFUnspecified,
    @SerialName("NameIDFEmail")
    NameIDFEmail,
    @SerialName("NameIDFX509")
    NameIDFX509,
    @SerialName("NameIDFWindows")
    NameIDFWindows,
    @SerialName("NameIDFKerberos")
    NameIDFKerberos,
    @SerialName("NameIDFEntity")
    NameIDFEntity,
    @SerialName("NameIDFPersistent")
    NameIDFPersistent,
    @SerialName("NameIDFTransient")
    NameIDFTransient
}

@Serializable
data class SSOSettingsResponse(
    @SerialName("default_sso_code")
    val defaultCode: String
)
