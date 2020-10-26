package com.wire.xenon.crypto.storage;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface SessionsDAO {
    @SqlUpdate("INSERT INTO Sessions (id, sid, data) VALUES (:id, :sid, :data) ON CONFLICT (id, sid) DO UPDATE SET data = EXCLUDED.data")
    int insert(@Bind("id") String id,
               @Bind("sid") String sid,
               @Bind("data") byte[] data);

    @SqlQuery("SELECT * FROM Sessions WHERE id = :id AND sid = :sid FOR UPDATE")
    @RegisterColumnMapper(_Mapper.class)
    Session get(@Bind("id") String id,
                @Bind("sid") String sid);

    @SqlUpdate("DELETE FROM Sessions WHERE id = :id")
    int delete(@Bind("id") String id);

    class _Mapper implements ColumnMapper<Session> {
        @Override
        public Session map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
            Session session = new Session();
            session.id = r.getString("id");
            session.sid = r.getString("sid");
            session.data = r.getBytes("data");
            return session;
        }
    }
}
