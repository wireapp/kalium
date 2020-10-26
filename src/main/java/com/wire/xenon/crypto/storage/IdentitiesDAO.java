package com.wire.xenon.crypto.storage;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface IdentitiesDAO {
    @SqlUpdate("INSERT INTO Identities (id, data) " +
            "VALUES (:id, :data) " +
            "ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data")
    int insert(@Bind("id") String id,
               @Bind("data") byte[] data);

    @SqlQuery("SELECT * FROM Identities WHERE id = :id")
    @RegisterColumnMapper(_Mapper.class)
    _Identity get(@Bind("id") String id);

    @SqlUpdate("DELETE FROM Identities WHERE id = :id")
    int delete(@Bind("id") String id);

    class _Mapper implements ColumnMapper<_Identity> {
        @Override
        public _Identity map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
            _Identity ret = new _Identity();
            ret.id = r.getString("id");
            ret.data = r.getBytes("data");
            return ret;
        }
    }

    class _Identity {
        public String id;
        public byte[] data;
    }
}
