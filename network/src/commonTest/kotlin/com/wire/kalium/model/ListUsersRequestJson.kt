package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.userDetails.QualifiedHandleListRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.QualifiedUserIdListRequest
import com.wire.kalium.network.api.base.model.QualifiedID

object ListUsersRequestJson {

    private val qualifiedIdsProvider = { serializable: QualifiedUserIdListRequest ->
        val idsArrayContent = serializable.qualifiedIds.joinToString(",") {
            """{"domain": "${it.domain}", "id":"${it.value}""""
        }
        """{"qualified_ids": [$idsArrayContent]}"""
    }
    private val qualifiedHandlesProvider = { serializable: QualifiedHandleListRequest ->
        val handlesArrayContent = serializable.qualifiedHandles.joinToString(",") {
            """{"domain": "${it.domain}", "handle":"${it.handle}""""
        }
        """{"qualified_ids": [$handlesArrayContent]}"""
    }

    val validIdsJsonProvider = ValidJsonProvider(
        QualifiedUserIdListRequest(
            listOf(
                QualifiedID("id1", "domain1"),
                QualifiedID("id11", "domain1"),
                QualifiedID("id2", "domain2")
            )
        ), qualifiedIdsProvider
    )

    val validHandlesJsonProvider = ValidJsonProvider(
        QualifiedUserIdListRequest(
            listOf(
                QualifiedID("id1", "domain1"),
                QualifiedID("id11", "domain1"),
                QualifiedID("id2", "domain2")
            )
        ), qualifiedIdsProvider
    )
}
