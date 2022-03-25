package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSearchResponse(
    @SerialName("documents") val documents: List<ContactDTO>,
    @SerialName("found") val found: Int,
    @SerialName("returned") val returned: Int,
    @SerialName("search_policy") val searchPolicy: SearchPolicyDTO,
    @SerialName("took") val took: Int
)

@Serializable
data class ContactDTO(
    @SerialName("accent_id") val accentId: Int?,
    @SerialName("handle") val handle: String?,
    @SerialName("id") val id: String?,
    @SerialName("name") val name: String,
    @SerialName("qualified_id") val qualifiedID: UserId,
    @SerialName("team") val team: String?
)

@Serializable
enum class SearchPolicyDTO {
    @SerialName("no_search")
    NO_SEARCH,

    @SerialName("exact_handle_search")
    EXACT_HANDLE_SEARCH,

    @SerialName("full_search")
    FULL_SEARCH;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
