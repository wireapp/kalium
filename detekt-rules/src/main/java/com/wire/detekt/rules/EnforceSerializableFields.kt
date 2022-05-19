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
class EnforceSerializableFields(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "Use Serializable annotations on every field of the Class that use @Serializable.",
        debt = Debt(mins = DEBT_IN_MINUTES_PER_MISSING_ANNOTATION)
    )

    override fun visitClassOrObject(kClass: KtClassOrObject) {
        for (superEntry in kClass.annotationEntries) {
            if (superEntry.text?.startsWith(SERIALIZABLE_CLASS_ANNOTATION) == true) {
                getDeclarationsOfInterestForClass(kClass).forEach { annotatedValue ->
                    println(annotatedValue.name)
                    val hasMatchingRequirement = annotatedValue.annotationEntries.any { annotation ->
                        val annotationName = annotation.shortName.toString()
                        annotationName == "SerialName" || annotationName == "Serializable" || annotationName == "Transient"
                    }
                    if (!hasMatchingRequirement) {
                        report(
                            kClass,
                            "The Serializable class '${kClass.name}' declares "
                                    + "a field '${annotatedValue.name}' without proper Serializable annotation "
                                    + "'@SerialName | @Serializable | @Transient'."
                        )
                    }
                }
            }
        }
    }

    private fun getDeclarationsOfInterestForClass(kClass: KtClassOrObject): List<KtAnnotated> =
        if (kClass is KtClass && kClass.isEnum()) {
            kClass.declarations.filterIsInstance<KtEnumEntry>()
        } else {
            kClass.getValueParameters()
        }

    private fun report(classOrObject: KtClassOrObject, message: String) {
        report(CodeSmell(issue, Entity.atName(classOrObject), message))
    }

    companion object {
        private const val DEBT_IN_MINUTES_PER_MISSING_ANNOTATION = 3
        private const val SERIALIZABLE_CLASS_ANNOTATION = "@Serializable"
    }
}
