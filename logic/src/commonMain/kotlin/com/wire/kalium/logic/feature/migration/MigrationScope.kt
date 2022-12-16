package com.wire.kalium.logic.feature.migration

import com.wire.kalium.logic.feature.conversation.PersistMigratedConversationUseCase
import com.wire.kalium.logic.feature.conversation.PersistMigratedConversationUseCaseImpl
import com.wire.kalium.persistence.db.UserDatabaseBuilder

internal class MigrationScope(
    private val userDatabase: UserDatabaseBuilder
) {

    val persistMigratedConversation: PersistMigratedConversationUseCase
        get() = PersistMigratedConversationUseCaseImpl(userDatabase.migrationDAO)

}
