/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class DaoFlowOnRuleTest(private val env: KotlinCoreEnvironment) {

    @Test
    fun `reports when asFlow is used without flowOn in DAOImpl`() {
        val code = """
            class MessageDAOImpl {
                fun observeMessages() {
                    return queries.selectAll()
                        .asFlow()
                        .mapToList()
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("must be followed by .flowOn() as the last operation")
    }

    @Test
    fun `does not report when flowOn is called as last operation in DAOImpl`() {
        val code = """
            class MessageDAOImpl {
                fun observeMessages() {
                    return queries.selectAll()
                        .asFlow()
                        .mapToList()
                        .flowOn(dispatcher)
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports when flowOn is not the last operation in DAOImpl`() {
        val code = """
            class MessageDAOImpl {
                fun observeMessages() {
                    return queries.selectAll()
                        .asFlow()
                        .flowOn(dispatcher)
                        .mapToList()
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report for non-DAOImpl classes`() {
        val code = """
            class MessageRepository {
                fun observeMessages() {
                    return queries.selectAll()
                        .asFlow()
                        .mapToList()
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when asFlow is not used`() {
        val code = """
            class MessageDAOImpl {
                fun getMessages() {
                    return queries.selectAll()
                        .executeAsList()
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports when asFlow is used without flowOn in DAO class`() {
        val code = """
            class MessageDAO {
                fun observeMessages() {
                    return queries.selectAll()
                        .asFlow()
                        .mapToList()
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("must be followed by .flowOn() as the last operation")
    }

    @Test
    fun `reports when asFlow is used without flowOn in Dao class`() {
        val code = """
            class MessageDao {
                fun observeMessages() {
                    return queries.selectAll()
                        .asFlow()
                        .mapToList()
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("must be followed by .flowOn() as the last operation")
    }

    @Test
    fun `reports when asFlow is used without flowOn in DaoImpl class`() {
        val code = """
            class MessageDaoImpl {
                fun observeMessages() {
                    return queries.selectAll()
                        .asFlow()
                        .mapToList()
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("must be followed by .flowOn() as the last operation")
    }

    @Test
    fun `does not report for class with DAO in middle of name`() {
        val code = """
            class MessageDAOWrapper {
                fun observeMessages() {
                    return queries.selectAll()
                        .asFlow()
                        .mapToList()
                }
            }
        """.trimIndent()

        val findings = DaoFlowOnRule(Config.empty).compileAndLintWithContext(env, code)
        assertThat(findings).isEmpty()
    }
}
