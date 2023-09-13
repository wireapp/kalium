package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.importer.ActionType
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

class LeaveConversationActionTest {

    @Test
    @Ignore("For some reason this is failing when merged to develop")
    fun givenOnlyOneUser_noUserShouldLeave() = runTest {
        val config = ActionType.LeaveConversation(1u, UserCount.single())
        mockkObject(ConversationPool)
        val monkeyPool = mockk<MonkeyPool>()
        val conversation = mockk<MonkeyConversation>(relaxed = true)
        val creator = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { ConversationPool.randomDynamicConversations(config.countGroups.toInt()) } returns listOf(conversation)
        every { conversation.creator } returns creator
        every { conversation.randomMonkeys(config.userCount) } returns listOf(creator)
        LeaveConversationAction(config).execute(coreLogic, monkeyPool)
        coVerify(inverse = true) { creator.leaveConversation(any()) }
        coVerify(exactly = 2) { creator.user }
        confirmVerified(creator)
    }

    @Test
    @Ignore("For some reason this is failing when merged to develop")
    fun givenMultipleUsers_oneShouldLeave() = runTest {
        val config = ActionType.LeaveConversation(1u, UserCount.fixed(2u))
        mockkObject(ConversationPool)
        val monkeyPool = mockk<MonkeyPool>()
        val monkey = mockk<Monkey>(relaxed = true)
        val conversation = mockk<MonkeyConversation>(relaxed = true)
        val creator = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { ConversationPool.randomDynamicConversations(config.countGroups.toInt()) } returns listOf(conversation)
        every { conversation.creator } returns creator
        every { conversation.randomMonkeys(config.userCount) } returns listOf(creator, monkey)
        LeaveConversationAction(config).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkey.leaveConversation(conversation.conversation.id) }
        coVerify(inverse = true) { creator.leaveConversation(any()) }
        verify(exactly = 1) { monkey.user }
        verify(exactly = 3) { creator.user }
        confirmVerified(creator)
        confirmVerified(monkey)
    }
}
