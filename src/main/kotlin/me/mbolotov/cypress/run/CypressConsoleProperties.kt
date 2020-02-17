package me.mbolotov.cypress.run

import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.javascript.testing.JsTestConsoleProperties

class CypressConsoleProperties(config: CypressRunConfig, executor: Executor, private val myLocator: SMTestLocator, val withTerminalConsole: Boolean) : JsTestConsoleProperties(config, "CypressTestRunner", executor) {
    init {
        isUsePredefinedMessageFilter = false
        setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false)
        setIfUndefined(TestConsoleProperties.HIDE_IGNORED_TEST, true)
        setIfUndefined(TestConsoleProperties.SCROLL_TO_SOURCE, true)
        setIfUndefined(TestConsoleProperties.SELECT_FIRST_DEFECT, true)
        isIdBasedTestTree = true
        isPrintTestingStartedTime = false
    }

    override fun getTestLocator(): SMTestLocator? {
        return myLocator
    }

    override fun createRerunFailedTestsAction(consoleView: ConsoleView?): AbstractRerunFailedTestsAction? {
        return CypressRerunFailedTestAction(consoleView as SMTRunnerConsoleView, this)
    }
}