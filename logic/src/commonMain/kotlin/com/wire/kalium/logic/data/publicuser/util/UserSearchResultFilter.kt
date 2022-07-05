package com.wire.kalium.logic.data.publicuser.util

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.contact.search.ContactDTO
import com.wire.kalium.persistence.dao.Member

class UserSearchResultFilter(
    private val idMapper: IdMapper = MapperProvider.idMapper()
) {

    fun contactExistInConversation(conversationMembers: List<Member>?, contactDTO: ContactDTO): Boolean {
        return conversationMembers?.map { idMapper.fromDaoModel(it.user) }
            ?.contains(idMapper.fromApiModel(contactDTO.qualifiedID)) ?: false
    }

}
