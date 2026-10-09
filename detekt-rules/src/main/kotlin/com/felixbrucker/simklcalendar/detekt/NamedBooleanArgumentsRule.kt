package com.felixbrucker.simklcalendar.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression

class NamedBooleanArgumentsRule(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = "NamedBooleanArguments",
    severity = Severity.Style,
    description = "Enforces named arguments when passing boolean literal values.",
    debt = Debt.FIVE_MINS
  )

  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)

    val args = expression.valueArguments
    if (args.isEmpty()) return

    for (arg in args) {
      val expr = arg.getArgumentExpression()
      if (expr is KtConstantExpression && (expr.text == "true" || expr.text == "false") && !arg.isNamed()) {
        report(
          CodeSmell(
            issue = issue,
            entity = Entity.from(arg),
            message = "Boolean literal argument '${expr.text}' in call to '${expression.calleeExpression?.text}' must use a named argument."
          )
        )
      }
    }
  }
}
