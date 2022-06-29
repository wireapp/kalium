package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.conversation.ConversationRepository

import com.wire.kalium.logic.feature.publicuser.search.ConversationMembersExcludedSearchUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersUseCase
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock

class ConversationMembersExcludedSearchUseCaseTest {

    private class Arrangement() {


        @Mock
        private val searchUsersUseCase: SearchUsersUseCase = mock(classOf<SearchUsersUseCase>())

        @Mock
        private val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())


        fun arrange() = this to ConversationMembersExcludedSearchUseCase(searchUsersUseCase, conversationRepository)
    }
}
