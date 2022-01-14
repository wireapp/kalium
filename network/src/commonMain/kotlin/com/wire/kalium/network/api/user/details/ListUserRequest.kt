package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.api.QualifiedHandle
import com.wire.kalium.network.api.QualifiedID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class ListUserRequest

@Serializable
data class QualifiedUserIdListRequest(
    @SerialName("qualified_ids") val qualifiedIds: List<QualifiedID>
) : ListUserRequest()

@Serializable
data class QualifiedHandleListRequest(
    @SerialName("qualified_handles") val qualifiedHandles: List<QualifiedHandle>
) : ListUserRequest()
