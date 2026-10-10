package com.felixbrucker.simklcalendar.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class AgentsRuleSetProvider : RuleSetProvider {
  override val ruleSetId: String = "agents"

  override fun instance(config: Config): RuleSet {
    return RuleSet(
      ruleSetId,
      listOf(
        ThreeSectionTestPatternRule(config),
        NamedBooleanArgumentsRule(config),
        ComposableLineLimitRule(config),
        StatelessChildComposableRule(config),
        NoDefaultParamsInInjectableConstructorRule(config),
        ForbiddenSharedPreferencesRule(config)
      )
    )
  }
}
