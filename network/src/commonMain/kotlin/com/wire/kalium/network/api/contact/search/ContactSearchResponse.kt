package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.api.QualifiedID
import kotlinx.serialization.SerialName

data class ContactSearchResponse(
    @SerialName("documents") val documents: List<Contact>,
    @SerialName("found") val found: Int,
    @SerialName("returned") val returned: Int,
    @SerialName("search_policy") val search_policy: String,
    @SerialName("took") val took: Int
)

data class Contact(
    @SerialName("accent_id") val accent_id: Int,
    @SerialName("handle") val handle: String,
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("qualified_id") val qualified_id: QualifiedID,
    @SerialName("team") val team: String
)
