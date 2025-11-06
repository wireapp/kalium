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

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * This rule enforces that in DAO classes (ending with "DAO", "DAOImpl", "Dao", or "DaoImpl"),
 * any call to `.asFlow()` must be followed by `.flowOn()` as the last operation in the chain.
 *
 * Examples:
 * - ✓ Correct: `query.asFlow().mapToList().flowOn(dispatcher)`
 * - ✗ Wrong: `query.asFlow().mapToList()` (missing flowOn)
 * - ✗ Wrong: `query.asFlow().flowOn(dispatcher).mapToList()` (flowOn is not last)
 */
class DaoFlowOnRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "DaoFlowOnRule",
        severity = Severity.Defect,
        description = "DAO classes must call .flowOn() as the last operation after .asFlow()",
        debt = Debt.FIVE_MINS
    )

    private var currentClassName: String? = null
    private var isInDao = false

    override fun visitClass(klass: KtClass) {
        currentClassName = klass.name
        isInDao = currentClassName?.let { name ->
            name.endsWith("DAO") ||
            name.endsWith("DAOImpl") ||
            name.endsWith("Dao") ||
            name.endsWith("DaoImpl")
        } ?: false
        super.visitClass(klass)
        isInDao = false
        currentClassName = null
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)

        if (!isInDao) return

        // Only check root expressions (those that don't have a parent DotQualifiedExpression)
        if (expression.parent is KtDotQualifiedExpression) return

        // Check if this expression contains .asFlow()
        if (containsAsFlow(expression)) {
            // Check if the last call in the chain is .flowOn()
            if (!endsWithFlowOn(expression)) {
                val message = "In $currentClassName, calls to .asFlow() must be followed by " +
                    ".flowOn() as the last operation in the chain"
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(expression),
                        message = message
                    )
                )
            }
        }
    }

    /**
     * Checks if the expression tree contains a call to .asFlow()
     */
    private fun containsAsFlow(expression: KtDotQualifiedExpression): Boolean {
        val text = expression.text
        return text.contains(".asFlow()")
    }

    /**
     * Checks if the expression ends with .flowOn()
     */
    private fun endsWithFlowOn(expression: KtDotQualifiedExpression): Boolean {
        val selectorText = expression.selectorExpression?.text ?: return false
        return selectorText.startsWith("flowOn(")
    }
}
