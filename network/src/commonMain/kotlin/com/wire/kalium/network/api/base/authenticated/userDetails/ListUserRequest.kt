package com.wire.kalium.network.api.base.authenticated.userDetails

import com.wire.kalium.network.api.base.model.QualifiedHandle
import com.wire.kalium.network.api.base.model.QualifiedID
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
