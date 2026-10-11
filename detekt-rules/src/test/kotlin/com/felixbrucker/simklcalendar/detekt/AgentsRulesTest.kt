package com.felixbrucker.simklcalendar.detekt

import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentsRulesTest {

  @Test
  fun testThreeSectionTestPatternRuleFlagsComment() {
    val code = """
      class ExampleTest {
        @Test
        fun testFoo() {
          // Arrange
          val x = 1
          // Act
          val y = x + 1
          // Assert
          assertEquals(2, y)
        }
      }
    """.trimIndent()

    val findings = ThreeSectionTestPatternRule().compileAndLint(code)

    assertEquals(1, findings.size)
  }

  @Test
  fun testNamedBooleanArgumentsRuleFlagsUnnamedLiteral() {
    val code = """
      fun doSomething(flag: Boolean, name: String) {}
      fun testCall() {
        doSomething(true, "hello")
      }
    """.trimIndent()

    val findings = NamedBooleanArgumentsRule().compileAndLint(code)

    assertEquals(1, findings.size)
  }

  @Test
  fun testForbiddenSharedPreferencesRuleFlagsImport() {
    val code = """
      import android.content.SharedPreferences

      class MyClass
    """.trimIndent()

    val findings = ForbiddenSharedPreferencesRule().compileAndLint(code)

    assertEquals(1, findings.size)
  }

  @Test
  fun testStatelessChildComposableRuleFlagsViewModelParam() {
    val code = """
      import androidx.compose.runtime.Composable

      class MyViewModel

      @Composable
      fun HeaderCard(viewModel: MyViewModel) {}
    """.trimIndent()

    val findings = StatelessChildComposableRule().compileAndLint(code)

    assertEquals(1, findings.size)
  }

  @Test
  fun testNoDefaultParamsInInjectableConstructorRuleFlagsDefaultParam() {
    val code = """
      import javax.inject.Inject

      class MyRepo @Inject constructor(val name: String = "default")
    """.trimIndent()

    val findings = NoDefaultParamsInInjectableConstructorRule().compileAndLint(code)

    assertEquals(1, findings.size)
  }
}
