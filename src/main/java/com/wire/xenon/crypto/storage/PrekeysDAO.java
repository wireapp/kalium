package com.wire.xenon.crypto.storage;

import com.wire.bots.cryptobox.PreKey;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface PrekeysDAO {
    @SqlUpdate("INSERT INTO Prekeys (id, kid, data) VALUES (:id, :kid, :data) ON CONFLICT (id, kid) DO UPDATE SET data = EXCLUDED.data")
    int insert(@Bind("id") String id,
               @Bind("kid") int kid,
               @Bind("data") byte[] data);

    @SqlQuery("SELECT kid, data FROM Prekeys WHERE id = :id")
    @RegisterColumnMapper(_Mapper.class)
    List<PreKey> get(@Bind("id") String id);

    @SqlUpdate("DELETE FROM Prekeys WHERE id = :id")
    int delete(@Bind("id") String id);

    class _Mapper implements ColumnMapper<PreKey> {

        @Override
        public PreKey map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
            int kid = r.getInt("kid");
            byte[] data = r.getBytes("data");
            return new PreKey(kid, data);
        }
    }
}
