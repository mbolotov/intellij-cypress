package me.mbolotov.cypress.run

import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.javascript.testing.JsTestConsoleProperties

class CypressConsoleProperties(config: CypressRunConfig, executor: Executor, val myLocator: SMTestLocator, val withTerminalConsole: Boolean) : JsTestConsoleProperties(config, "CypressTestRunner", executor) {
    init {
        isUsePredefinedMessageFilter = false
        setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false)
        setIfUndefined(TestConsoleProperties.HIDE_IGNORED_TEST, true)
        setIfUndefined(TestConsoleProperties.SCROLL_TO_SOURCE, true)
        setIfUndefined(TestConsoleProperties.SELECT_FIRST_DEFECT, true)
        isIdBasedTestTree = true
        isPrintTestingStartedTime = false
    }
}