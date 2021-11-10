package com.wire.kalium.helium

import org.apache.log4j.BasicConfigurator
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.sql.Driver
import java.sql.DriverManager

object DatabaseTestBase {
    internal var flyway: Flyway? = null
    internal var jdbi: Jdbi? = null

    @BeforeAll
    @Throws(Exception::class)
    fun initiate() {
        BasicConfigurator.configure()
        var databaseUrl = System.getenv("POSTGRES_URL")
        databaseUrl = "jdbc:postgresql://" + (databaseUrl ?: "localhost/helium")
        val user = System.getenv("POSTGRES_USER")
        val password = System.getenv("POSTGRES_PASSWORD")
        val driverClass = Class.forName("org.postgresql.Driver")
        val driver = driverClass.getDeclaredConstructor().newInstance() as Driver
        DriverManager.registerDriver(driver)
        jdbi = (if (password != null) Jdbi.create(databaseUrl, user, password) else Jdbi.create(databaseUrl))
                .installPlugin(SqlObjectPlugin())
        flyway = Flyway
                .configure()
                .dataSource(databaseUrl, user, password)
                .baselineOnMigrate(true)
                .load()
        flyway.migrate()
    }

    @AfterAll
    fun classCleanup() {
        flyway.clean()
    }
}