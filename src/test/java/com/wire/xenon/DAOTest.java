package com.wire.xenon;

import com.wire.xenon.crypto.storage.IdentitiesDAO;
import com.wire.xenon.state.StatesDAO;
import org.apache.log4j.BasicConfigurator;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.UUID;

public class DAOTest {
    private static final String url = "jdbc:postgresql://localhost/xenon";
    private static final Jdbi jdbi = Jdbi.create(url)
            .installPlugin(new SqlObjectPlugin());

    @BeforeClass
    public static void before() throws Exception {
        BasicConfigurator.configure();

        Class<?> driverClass = Class.forName("org.postgresql.Driver");
        final Driver driver = (Driver) driverClass.newInstance();
        DriverManager.registerDriver(driver);

        // Migrate DB if needed
        Flyway flyway = Flyway
                .configure()
                .dataSource(url, null, null)
                .load();
        flyway.migrate();
    }

    @Test
    public void testIdentitiesDAO() {
        final IdentitiesDAO identitiesDAO = jdbi.onDemand(IdentitiesDAO.class);
        final String id = UUID.randomUUID().toString();

        final int insert = identitiesDAO.insert(id, id.getBytes());
        final byte[] bytes = identitiesDAO.get(id).data;
        final int delete = identitiesDAO.delete(id);
    }

    @Test
    public void testStatesDAO() {
        final StatesDAO statesDAO = jdbi.onDemand(StatesDAO.class);
        final UUID id = UUID.randomUUID();
        final String text = "{\"some\" : \"text\"}";

        final int insert = statesDAO.insert(id, text);
        final String dbText = statesDAO.get(id);
        final int delete = statesDAO.delete(id);
    }
}
