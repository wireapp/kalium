package com.wire.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Wire's [RuleSetProvider] can be extended to add more rules to this [RuleSet]
 */
class WireRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "WireRuleSet"

    override fun instance(config: Config) = RuleSet(ruleSetId, listOf(EnforceSerializableFields(config)))
}
