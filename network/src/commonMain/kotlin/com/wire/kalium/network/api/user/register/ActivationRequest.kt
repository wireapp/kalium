package com.wire.kalium.network.api.user.register


import com.wire.kalium.network.api.AssetKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
internal data class RequestActivationRequest(
    @SerialName("email")
    val email: String?,
    @SerialName("locale")
    val locale: String?,
    @SerialName("phone")
    val phone: String?,
    @SerialName("voice_call")
    val voiceCall: Boolean?
)

@Serializable
internal data class ActivationRequest(
    @SerialName("code")
    val code: String,
    @SerialName("dryrun")
    val dryRun: Boolean?,
    @SerialName("email")
    val email: String?,
    @SerialName("key")
    val key: String?,
    @SerialName("label")
    val label: String?,
    @SerialName("phone")
    val phone: String?
)

@Serializable
internal data class NewBindingTeamDTO(
    @SerialName("currency")
    val currency: String?,
    @SerialName("icon")
    val iconAssetId: String, // todo(assets): temp fix, we should replace with [AssetId] once domain avb on server config (api-version pr)
    @SerialName("icon_key")
    val iconKey: AssetKey?,
    @SerialName("name")
    val name: String
)
