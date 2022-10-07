package com.wire.kalium.persistence.dao.reaction

data class ReactionsEntity(
    val totalReactions: ReactionsCountEntity,
    val selfUserReactions: UserReactionsEntity
){
    companion object {
        val EMPTY = ReactionsEntity(emptyMap(), emptySet())
    }
}

typealias ReactionsCountEntity = Map<String, Int>
typealias UserReactionsEntity = Set<String>
