package com.wire.kalium.network.api.pagination

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaginationRequest(
    @SerialName("paging_state")
    val pagingState: String?,
    @SerialName("size")
    val size: Int? = null // Set in case you want specific number of pages, otherwise, the backend will return default per endpoint
)
