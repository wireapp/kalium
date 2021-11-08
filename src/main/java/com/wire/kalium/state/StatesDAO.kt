package com.wire.kalium.state

import java.util.UUID
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery

interface StatesDAO {
    @SqlUpdate(
        "INSERT INTO States (botId, bot) " +
                "VALUES (:botId, to_json(:bot::json)) " +
                "ON CONFLICT (botId) DO UPDATE SET bot = EXCLUDED.bot"
    )
    open fun insert(
        @Bind("botId") botId: UUID?,
        @Bind("bot") bot: String?
    ): Int

    @SqlQuery("SELECT bot FROM States WHERE botId = :botId")
    open operator fun get(@Bind("botId") botId: UUID?): String?
    @SqlUpdate("DELETE FROM States WHERE botId = :botId")
    open fun delete(@Bind("botId") botId: UUID?): Int
}
