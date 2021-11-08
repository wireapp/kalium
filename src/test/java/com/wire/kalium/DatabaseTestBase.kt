package com.wire.kalium

import kotlin.Throws
import org.jdbi.v3.core.Jdbi
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import java.sql.DriverManager
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.junit.jupiter.api.AfterAll
import java.sql.Driver

abstract class DatabaseTestBase {
    companion object {
        protected var flyway: Flyway? = null
        protected var jdbi: Jdbi? = null
        @BeforeAll
        @Throws(Exception::class)
        fun initiate() {
            var databaseUrl = System.getenv("POSTGRES_URL")
            databaseUrl = "jdbc:postgresql://" + (databaseUrl ?: "localhost/lithium")
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
}
