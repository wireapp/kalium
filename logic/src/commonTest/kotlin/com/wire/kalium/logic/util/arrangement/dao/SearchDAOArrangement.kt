/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.util.arrangement.dao

import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.SearchDAO
import com.wire.kalium.persistence.dao.UserSearchEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

internal interface SearchDAOArrangement {
    @Mock
    val searchDAO: SearchDAO

    fun withGetKnownContacts(
        result: List<UserSearchEntity>
    )

    fun withGetKnownContactsExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity> = any()
    )

    fun withSearchList(
        result: List<UserSearchEntity>,
        query: Matcher<String> = any()
    )

    fun withSearchListExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity> = any(),
        query: Matcher<String> = any()
    )

    fun withSearchByHandle(
        result: List<UserSearchEntity>,
        handle: Matcher<String> = any()
    )

    fun withSearchByHandleExcludingConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity> = any(),
        handle: Matcher<String> = any()
    )
}

internal class SearchDAOArrangementImpl : SearchDAOArrangement {

    @Mock
    override val searchDAO: SearchDAO = mock(SearchDAO::class)

    override fun withGetKnownContacts(
        result: List<UserSearchEntity>
    ) {
        given(searchDAO)
            .suspendFunction(searchDAO::getKnownContacts)
            .whenInvoked()
            .then { result }
    }

    override fun withGetKnownContactsExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity>
    ) {
        given(searchDAO)
            .suspendFunction(searchDAO::getKnownContactsExcludingAConversation)
            .whenInvokedWith(conversationId)
            .then { result }
    }

    override fun withSearchList(result: List<UserSearchEntity>, query: Matcher<String>) {
        given(searchDAO)
            .suspendFunction(searchDAO::searchList)
            .whenInvokedWith(query)
            .then { result }
    }

    override fun withSearchListExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity>,
        query: Matcher<String>
    ) {
        given(searchDAO)
            .suspendFunction(searchDAO::searchListExcludingAConversation)
            .whenInvokedWith(conversationId, query)
            .thenReturn(result)
    }

    override fun withSearchByHandle(result: List<UserSearchEntity>, handle: Matcher<String>) {
        given(searchDAO)
            .suspendFunction(searchDAO::handleSearch)
            .whenInvokedWith(handle)
            .thenReturn(result)
    }

    override fun withSearchByHandleExcludingConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity>,
        handle: Matcher<String>
    ) {
        given(searchDAO)
            .suspendFunction(searchDAO::handleSearchExcludingAConversation)
            .whenInvokedWith(handle, conversationId)
            .thenReturn(result)
    }
}
