package com.wire.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPublic

class DocumentedPublicUseCases(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "Public Use Cases should have documentation for the clients.",
        debt = Debt(mins = DEBT_IN_MINUTES_PER_MISSING_ANNOTATION)
    )

    override fun visitClassOrObject(kClass: KtClassOrObject) {
        if (kClass.javaClass.packageName.contains("com.wire.kalium.logic.feature") && kClass.isPublic) {
            val hasDocumentation = kClass.docComment?.getAllSections()?.firstOrNull()?.getContent() != null
            if (!hasDocumentation) {
                report(kClass, "The class '${kClass.name}' is a Public Use Case and doesn't provide documentation.")
            }
        }
    }

    companion object {
        private const val DEBT_IN_MINUTES_PER_MISSING_ANNOTATION = 1
    }
}
