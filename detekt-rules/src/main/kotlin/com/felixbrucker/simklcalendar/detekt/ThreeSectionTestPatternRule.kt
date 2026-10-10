package com.felixbrucker.simklcalendar.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtNamedFunction

class ThreeSectionTestPatternRule(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = "ThreeSectionTestPattern",
    severity = Severity.Style,
    description = "Enforces the strict 3-section (Arrange-Act-Assert) pattern for unit tests.",
    debt = Debt.FIVE_MINS
  )

  private val sectionCommentRegex = Regex(
    """^\s*//\s*(arrange|act|assert|given|when|then|setup)\b""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    super.visitNamedFunction(function)

    val isTest = function.annotationEntries.any {
      val name = it.shortName?.asString()
      name == "Test"
    }
    if (!isTest) return

    val bodyText = function.bodyBlockExpression?.text ?: return

    if (sectionCommentRegex.containsMatchIn(bodyText)) {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(function),
          message = "Test '${function.name}' contains section header comments (e.g. // Arrange, // Act, // Assert). Remove labeling comments."
        )
      )
    }
  }
}
