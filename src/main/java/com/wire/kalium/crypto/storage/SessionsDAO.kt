package com.wire.kalium.crypto.storage

import kotlin.Throws
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper
import java.sql.SQLException
import java.sql.ResultSet
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.core.mapper.ColumnMapper

interface SessionsDAO {
    @SqlUpdate("INSERT INTO Sessions (id, sid, data) VALUES (:id, :sid, :data) ON CONFLICT (id, sid) DO UPDATE SET data = EXCLUDED.data")
    open fun insert(
        @Bind("id") id: String?,
        @Bind("sid") sid: String?,
        @Bind("data") data: ByteArray?
    ): Int

    @SqlQuery("SELECT * FROM Sessions WHERE id = :id AND sid = :sid FOR UPDATE")
    @RegisterColumnMapper(_Mapper::class)
    open operator fun get(
        @Bind("id") id: String?,
        @Bind("sid") sid: String?
    ): Session?

    @SqlUpdate("DELETE FROM Sessions WHERE id = :id")
    open fun delete(@Bind("id") id: String?): Int
    class _Mapper : ColumnMapper<Session?> {
        @Throws(SQLException::class)
        override fun map(r: ResultSet?, columnNumber: Int, ctx: StatementContext?): Session? {
            val session = Session()
            session.id = r.getString("id")
            session.sid = r.getString("sid")
            session.data = r.getBytes("data")
            return session
        }
    }
}
