package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.importer.ActionType
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SendMessageActionTest {

    @Test
    fun givenEmptyTargets_randomConversationsShouldBePicked() = runTest {
        val config = ActionType.SendMessage(UserCount.single(), 1u, 1u, listOf())
        val monkeyPool = mockk<MonkeyPool>()
        mockkObject(ConversationPool)
        val monkey = mockk<Monkey>(relaxed = true)
        val conversation = mockk<MonkeyConversation>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { ConversationPool.randomConversations(config.countGroups) } returns listOf(conversation)
        every { conversation.randomMonkeys(config.userCount) } returns listOf(monkey)
        SendMessageAction(config).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkey.sendMessageTo(any(), any()) }
        verify { conversation.randomMonkeys(config.userCount) }
        verify { conversation.conversation }
        verify { conversation.conversation.name }
        verify { conversation.conversation.id }
        confirmVerified(monkey, conversation)
    }

    @Test
    fun givenTargets_PrefixedConversationShouldBePicked() = runTest {
        val config = ActionType.SendMessage(UserCount.single(), 1u, 1u, listOf("group1"))
        val monkeyPool = mockk<MonkeyPool>()
        mockkObject(ConversationPool)
        val monkey = mockk<Monkey>(relaxed = true)
        val conversation = mockk<MonkeyConversation>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { ConversationPool.getFromPrefixed("group1") } returns listOf(conversation)
        every { conversation.randomMonkeys(config.userCount) } returns listOf(monkey)
        SendMessageAction(config).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkey.sendMessageTo(any(), any()) }
        verify { conversation.randomMonkeys(config.userCount) }
        verify { conversation.conversation }
        verify { conversation.conversation.name }
        verify { conversation.conversation.id }
        confirmVerified(monkey, conversation)
    }

    @Test
    fun givenOne21Informed_directMessageShouldBeSent() = runTest {
        val config = ActionType.SendMessage(UserCount.single(), 1u, 1u, listOf("One21"))
        val monkeyPool = mockk<MonkeyPool>()
        mockkObject(ConversationPool)
        val monkey = mockk<Monkey>(relaxed = true)
        val targetMonkey = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { monkeyPool.randomLoggedInMonkeys(config.userCount) } returns listOf(monkey)
        coEvery { monkey.randomPeer(monkeyPool) } returns targetMonkey
        SendMessageAction(config).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkey.sendDirectMessageTo(targetMonkey, any()) }
        coVerify(exactly = 1) { monkey.randomPeer(monkeyPool) }
        confirmVerified(monkey)
    }

    @Test
    fun givenOne21AndTargetInformed_multipleMessagesShouldBeSent() = runTest {
        val config = ActionType.SendMessage(UserCount.single(), 1u, 1u, listOf("One21", "group1"))
        val monkeyPool = mockk<MonkeyPool>()
        mockkObject(ConversationPool)
        val monkey = mockk<Monkey>(relaxed = true)
        val targetMonkey = mockk<Monkey>(relaxed = true)
        val conversation = mockk<MonkeyConversation>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { ConversationPool.getFromPrefixed("group1") } returns listOf(conversation)
        every { conversation.randomMonkeys(config.userCount) } returns listOf(monkey)

        every { monkeyPool.randomLoggedInMonkeys(config.userCount) } returns listOf(monkey)
        coEvery { monkey.randomPeer(monkeyPool) } returns targetMonkey
        SendMessageAction(config).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkey.sendDirectMessageTo(targetMonkey, any()) }
        coVerify(exactly = 1) { monkey.sendMessageTo(any(), any()) }
        coVerify(exactly = 1) { monkey.randomPeer(monkeyPool) }
        verify { conversation.randomMonkeys(config.userCount) }
        verify { conversation.conversation }
        verify { conversation.conversation.name }
        verify { conversation.conversation.id }
        confirmVerified(monkey, conversation)
    }
}
