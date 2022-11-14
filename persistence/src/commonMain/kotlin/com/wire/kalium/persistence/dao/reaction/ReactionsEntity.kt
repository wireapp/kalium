package com.wire.kalium.persistence.dao.reaction

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity

data class ReactionsEntity(
    val totalReactions: ReactionsCountEntity,
    val selfUserReactions: UserReactionsEntity
){
    companion object {
        val EMPTY = ReactionsEntity(emptyMap(), emptySet())
    }
}

data class MessageReactionEntity(
    val emoji: String,
    val userId: QualifiedIDEntity,
    val name: String?,
    val handle: String?,
    val previewAssetIdEntity: QualifiedIDEntity?,
    val userTypeEntity: UserTypeEntity,
    val deleted: Boolean,
    val connectionStatus: ConnectionEntity.State,
    val availabilityStatus: UserAvailabilityStatusEntity
)

typealias ReactionsCountEntity = Map<String, Int>
typealias UserReactionsEntity = Set<String>
