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

interface IdentitiesDAO {
    @SqlUpdate(
        "INSERT INTO Identities (id, data) " +
                "VALUES (:id, :data) " +
                "ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data"
    )
    open fun insert(
        @Bind("id") id: String?,
        @Bind("data") data: ByteArray?
    ): Int

    @SqlQuery("SELECT * FROM Identities WHERE id = :id")
    @RegisterColumnMapper(_Mapper::class)
    open operator fun get(@Bind("id") id: String?): _Identity?
    @SqlUpdate("DELETE FROM Identities WHERE id = :id")
    open fun delete(@Bind("id") id: String?): Int
    class _Mapper : ColumnMapper<_Identity?> {
        @Throws(SQLException::class)
        override fun map(r: ResultSet?, columnNumber: Int, ctx: StatementContext?): _Identity? {
            val ret = _Identity()
            ret.id = r.getString("id")
            ret.data = r.getBytes("data")
            return ret
        }
    }

    class _Identity {
        var id: String? = null
        var data: ByteArray?
    }
}
