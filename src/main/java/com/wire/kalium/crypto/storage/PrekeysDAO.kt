package com.wire.kalium.crypto.storage

import kotlin.Throws
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper
import java.sql.SQLException
import java.sql.ResultSet
import org.jdbi.v3.core.statement.StatementContext
import com.wire.bots.cryptobox.PreKey
import org.jdbi.v3.core.mapper.ColumnMapper

interface PrekeysDAO {
    @SqlUpdate("INSERT INTO Prekeys (id, kid, data) VALUES (:id, :kid, :data) ON CONFLICT (id, kid) DO UPDATE SET data = EXCLUDED.data")
    open fun insert(
        @Bind("id") id: String?,
        @Bind("kid") kid: Int,
        @Bind("data") data: ByteArray?
    ): Int

    @SqlQuery("SELECT kid, data FROM Prekeys WHERE id = :id")
    @RegisterColumnMapper(_Mapper::class)
    open operator fun get(@Bind("id") id: String?): MutableList<PreKey?>?
    @SqlUpdate("DELETE FROM Prekeys WHERE id = :id")
    open fun delete(@Bind("id") id: String?): Int
    class _Mapper : ColumnMapper<PreKey?> {
        @Throws(SQLException::class)
        override fun map(r: ResultSet?, columnNumber: Int, ctx: StatementContext?): PreKey? {
            val kid = r.getInt("kid")
            val data = r.getBytes("data")
            return PreKey(kid, data)
        }
    }
}
