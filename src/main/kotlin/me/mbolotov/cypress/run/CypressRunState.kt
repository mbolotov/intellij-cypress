package me.mbolotov.cypress.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.javascript.debugger.CommandLineDebugConfigurator
import com.intellij.javascript.nodejs.NodeCommandLineUtil
import com.intellij.javascript.nodejs.NodeConsoleAdditionalFilter
import com.intellij.javascript.nodejs.NodeStackTraceFilter
import com.intellij.javascript.nodejs.debug.NodeLocalDebuggableRunProfileStateSync
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.nodejs.util.NodeJsCodeLocator
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

class CypressRunState(private val myEnv: ExecutionEnvironment, private val myRunConfiguration: CypressRunConfig) : NodeLocalDebuggableRunProfileStateSync() {
    private val myProject = myEnv.project

    override fun executeSync(configurator: CommandLineDebugConfigurator?): ExecutionResult {
        val interpreter: NodeJsInterpreter = NodeJsInterpreterRef.create(this.myRunConfiguration.getPersistentData().nodeJsRef).resolveNotNull(myEnv.project)
        val commandLine = NodeCommandLineUtil.createCommandLine(if (SystemInfo.isWindows) false else null)
        NodeCommandLineUtil.configureCommandLine(commandLine, configurator) { debugMode: Boolean -> this.configureCommandLine(commandLine, interpreter, debugMode) }
        val processHandler = NodeCommandLineUtil.createProcessHandler(commandLine, false)
        val consoleProperties = CypressConsoleProperties(this.myRunConfiguration, this.myEnv.getExecutor(), CypressTestLocationProvider(), NodeCommandLineUtil.shouldUseTerminalConsole(processHandler))
        val consoleView: ConsoleView = this.createSMTRunnerConsoleView(commandLine.workDirectory, consoleProperties)
        ProcessTerminatedListener.attach(processHandler)
        consoleView.attachToProcess(processHandler)
        val executionResult = DefaultExecutionResult(consoleView, processHandler)
//        executionResult.setRestartActions(consoleProperties.createRerunFailedTestsAction(consoleView))
        return executionResult
    }

    private fun createSMTRunnerConsoleView(workingDirectory: File?, consoleProperties: CypressConsoleProperties): ConsoleView {
        val consoleView: ConsoleView = SMTestRunnerConnectionUtil.createConsole(consoleProperties.testFrameworkName, consoleProperties)
        consoleProperties.addStackTraceFilter(NodeStackTraceFilter(this.myProject, workingDirectory))
        consoleProperties.stackTrackFilters.forEach { consoleView.addMessageFilter(it) }
        consoleView.addMessageFilter(NodeConsoleAdditionalFilter(this.myProject, workingDirectory))
        Disposer.register(this.myProject, consoleView)
        return consoleView
    }

    private fun configureCommandLine(commandLine: GeneralCommandLine, interpreter: NodeJsInterpreter, debugMode: Boolean) {
        commandLine.charset = StandardCharsets.UTF_8
        val data = this.myRunConfiguration.getPersistentData()
        val workingDirectory = data.getWorkingDirectory()
        if (workingDirectory.isNotBlank()) {
            commandLine.withWorkDirectory(workingDirectory)
        }
        NodeCommandLineUtil.configureUsefulEnvironment(commandLine)
        NodeCommandLineUtil.prependNodeDirToPATH(commandLine, interpreter)
        commandLine.withParameters("node_modules/cypress/bin/cypress", "run")
        if (data.additionalParams.isNotBlank()) {
            commandLine.withParameters(data.additionalParams.split("\\s+".toRegex()))
        }
        EnvironmentVariablesData.create(data.envs, data.passParentEnvs).configureCommandLine(commandLine, true)
        if (debugMode) {
        }
        commandLine.addParameter("--reporter")
        commandLine.addParameter(getMochaReporterFile().absolutePath)
        when (data.kind) {
            CypressRunConfig.TestKind.DIRECTORY -> {
                // todo integration folder
                commandLine.addParameter(FileUtil.toSystemDependentName(data.specsDir!!))
            }
            CypressRunConfig.TestKind.SPEC -> {
                commandLine.withParameters("-s", data.specFile)
            }
            CypressRunConfig.TestKind.TEST -> TODO()
            CypressRunConfig.TestKind.SUITE -> TODO()
        }
        NodeCommandLineConfigurator.find(interpreter).configure(commandLine)
    }

    private fun getMochaReporterFile(): File {
        return try {
            NodeJsCodeLocator.getFileRelativeToJsDir("mocha-intellij/lib/mochaIntellijReporter.js")
        } catch (e: IOException) {
            throw ExecutionException("IntelliJ Mocha reporter not found", e)
        }
    }
}
