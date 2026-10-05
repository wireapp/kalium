package com.wire.kalium.logic.data.message

import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationAppsContentMappingTest {
    @Test
    fun initialAppsNoticeHasItsOwnStorageVariant() {
        assertEquals(
            MessageEntityContent.NewConversationAppsEnabled,
            MessageContent.NewConversationAppsEnabled.toMessageEntityContent(),
        )
        assertEquals(
            MessageContent.NewConversationAppsEnabled,
            MessageEntityContent.NewConversationAppsEnabled.toMessageContent(),
        )
    }

    @Test
    fun legacyAccessChangesRemainChangesWithoutGuessingInitialNoticeProvenance() {
        listOf(true, false).forEach { enabled ->
            assertEquals(
                MessageContent.ConversationAppsEnabledChanged(enabled),
                MessageEntityContent.ConversationAppsAccessChanged(enabled).toMessageContent(),
            )
            assertEquals(
                MessageEntityContent.ConversationAppsAccessChanged(enabled),
                MessageContent.ConversationAppsEnabledChanged(enabled).toMessageEntityContent(),
            )
        }
    }
}
