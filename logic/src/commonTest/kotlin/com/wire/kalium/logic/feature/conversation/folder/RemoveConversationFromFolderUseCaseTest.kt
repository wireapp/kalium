/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.conversation.folder

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.ConversationId
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class RemoveConversationFromFolderUseCaseTest {
    private val conversation = ConversationId("conversation", "example.com")
    private val repository = mock<ConversationFolderRepository>()
    private val useCase = RemoveConversationFromFolderUseCaseImpl(repository)

    @Test
    fun removingLastConversationKeepsFolderAndSyncsOnce() = runTest {
        everySuspend { repository.removeConversationFromFolder(conversation, "work") } returns Either.Right(Unit)
        everySuspend { repository.syncConversationFoldersFromLocal() } returns Either.Right(Unit)
        assertIs<RemoveConversationFromFolderUseCase.Result.Success>(useCase(conversation, "work"))
        verifySuspend(VerifyMode.exactly(1)) { repository.syncConversationFoldersFromLocal() }
        verifySuspend(VerifyMode.not) { repository.removeFolder(any()) }
    }

    @Test
    fun storageFailureDoesNotSync() = runTest {
        everySuspend { repository.removeConversationFromFolder(conversation, "work") } returns Either.Left(CoreFailure.Unknown(null))
        assertIs<RemoveConversationFromFolderUseCase.Result.Failure>(useCase(conversation, "work"))
        verifySuspend(VerifyMode.not) { repository.syncConversationFoldersFromLocal() }
    }

    @Test
    fun syncFailureIsReported() = runTest {
        everySuspend { repository.removeConversationFromFolder(conversation, "work") } returns Either.Right(Unit)
        everySuspend { repository.syncConversationFoldersFromLocal() } returns Either.Left(CoreFailure.Unknown(null))
        assertIs<RemoveConversationFromFolderUseCase.Result.Failure>(useCase(conversation, "work"))
    }
}
