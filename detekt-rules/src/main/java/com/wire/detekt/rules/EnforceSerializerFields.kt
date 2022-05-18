package com.wire.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getValueParameters

@Suppress("NestedBlockDepth")
class EnforceSerializerFields(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Use Serializable annotations on every field of the Class that use @Serializable.",
        Debt.FIVE_MINS
    )

    override fun visitClassOrObject(kClass: KtClassOrObject) {
        for (superEntry in kClass.annotationEntries) {
            if (superEntry.text?.startsWith(SERIALIZABLE_CLASS_ANNOTATION) == true) {
                kClass.getValueParameters().forEach { parameter ->
                    val hasMatchingRequirement = parameter.annotationEntries.any { annotation ->
                        val annotationName = annotation.shortName.toString()
                        annotationName == "SerialName" || annotationName == "Serializable" || annotationName == "Transient"
                    }
                    if (!hasMatchingRequirement) {
                        report(
                            kClass,
                            "The class '${kClass.name}' needs to implement Serializable annotations on every attribute' " +
                                    "@SerialName | @Serializable | @Transient'."
                        )
                    }
                }
            }
        }
    }

    private fun report(classOrObject: KtClassOrObject, message: String) {
        report(CodeSmell(issue, Entity.atName(classOrObject), message))
    }

    companion object {
        private const val SERIALIZABLE_CLASS_ANNOTATION = "@Serializable"
    }
}
