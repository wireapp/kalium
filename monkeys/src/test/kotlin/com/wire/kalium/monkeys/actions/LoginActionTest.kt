package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.importer.ActionType
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.pool.MonkeyPool
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoginActionTest {

    @Test
    fun givenLoginConfigProvided_thenShouldLogin() = runTest {
        mockkObject(MonkeyPool)
        val monkey = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { MonkeyPool.randomLoggedOutMonkeys(UserCount.single()) } returns listOf(monkey)
        LoginAction(ActionType.Login(UserCount.single())).execute(coreLogic)
        coVerify(exactly = 1) { monkey.login(coreLogic, MonkeyPool::loggedIn) }
        coVerify(exactly = 0) { monkey.logout(MonkeyPool::loggedOut) }
        confirmVerified(monkey)
    }

    @Test
    fun givenLoginConfigWithDurationProvided_thenShouldLoginAndLogout() = runTest {
        mockkObject(MonkeyPool)
        val monkey = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { MonkeyPool.randomLoggedOutMonkeys(UserCount.single()) } returns listOf(monkey)
        LoginAction(ActionType.Login(UserCount.single(), 10u)).execute(coreLogic)
        coVerify(exactly = 1) { monkey.login(coreLogic, MonkeyPool::loggedIn) }
        coVerify(exactly = 1) { monkey.logout(MonkeyPool::loggedOut) }
        confirmVerified(monkey)
    }
}
