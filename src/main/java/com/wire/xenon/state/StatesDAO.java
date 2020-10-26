package com.wire.xenon.state;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.UUID;

public interface StatesDAO {
    @SqlUpdate("INSERT INTO States (botId, bot) VALUES (:botId, to_json(:bot::json)) ON CONFLICT (botId) DO UPDATE SET bot = EXCLUDED.bot")
    int insert(@Bind("botId") UUID botId,
               @Bind("bot") String bot);

    @SqlQuery("SELECT bot FROM States WHERE botId = :botId")
    String get(@Bind("botId") UUID botId);

    @SqlUpdate("DELETE FROM States WHERE botId = :botId")
    int delete(@Bind("botId") UUID botId);
}
