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
package com.wire.kalium.logic.util.arrangement.mls

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigrator
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

interface OneOnOneMigratorArrangement {

    val oneOnOneMigrator: OneOnOneMigrator

    suspend fun withMigrateToMLSReturns(result: Either<CoreFailure, ConversationId>)

<<<<<<< HEAD
    suspend fun withMigrateToProteusReturns(result: Either<CoreFailure, ConversationId>)

    suspend fun withMigrateExistingToProteusReturns(result: Either<CoreFailure, ConversationId>)
=======
    fun withMigrateToProteusReturns(result: Either<CoreFailure, ConversationId>)

    fun withMigrateExistingToProteusReturns(result: Either<CoreFailure, ConversationId>)
>>>>>>> f9fcff1f31 (fix: port migration 1 on 1 resolution for mls migration (WPB-15191) (WPB-11194) (#3221))
}

class OneOnOneMigratorArrangementImpl : OneOnOneMigratorArrangement {

    @Mock
    override val oneOnOneMigrator = mock(OneOnOneMigrator::class)

    override suspend fun withMigrateToMLSReturns(result: Either<CoreFailure, ConversationId>) {
        coEvery {
            oneOnOneMigrator.migrateToMLS(any())
        }.returns(result)
    }

    override suspend fun withMigrateToProteusReturns(result: Either<CoreFailure, ConversationId>) {
        coEvery {
            oneOnOneMigrator.migrateToProteus(any())
        }.returns(result)
    }

    override suspend fun withMigrateExistingToProteusReturns(result: Either<CoreFailure, ConversationId>) {
        coEvery {
            oneOnOneMigrator.migrateExistingProteus(any())
        }.returns(result)
    }

    override fun withMigrateExistingToProteusReturns(result: Either<CoreFailure, ConversationId>) {
        given(oneOnOneMigrator)
            .suspendFunction(oneOnOneMigrator::migrateExistingProteus)
            .whenInvokedWith(any())
            .thenReturn(result)
    }
}
