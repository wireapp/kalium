package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.api.QualifiedHandle
import com.wire.kalium.network.api.QualifiedID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class ListUserRequest {
    companion object
}

fun ListUserRequest.Companion.qualifiedIds(qualifiedIDs: List<QualifiedID>) = QualifiedUserIdListRequest(qualifiedIDs)

@Serializable
data class QualifiedUserIdListRequest(
    @SerialName("qualified_ids") val qualifiedIds: List<QualifiedID>
) : ListUserRequest()


fun ListUserRequest.Companion.qualifiedHandles(qualifiedHandles: List<QualifiedHandle>) = QualifiedHandleListRequest(qualifiedHandles)

@Serializable
data class QualifiedHandleListRequest(
    @SerialName("qualified_handles") val qualifiedHandles: List<QualifiedHandle>
) : ListUserRequest()
