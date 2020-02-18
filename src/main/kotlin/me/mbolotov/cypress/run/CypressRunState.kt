package me.mbolotov.cypress.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.javascript.debugger.CommandLineDebugConfigurator
import com.intellij.javascript.nodejs.*
import com.intellij.javascript.nodejs.debug.NodeLocalDebuggableRunProfileStateSync
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.charset.StandardCharsets

class CypressRunState(private val myEnv: ExecutionEnvironment, private val myRunConfiguration: CypressRunConfig) : NodeLocalDebuggableRunProfileStateSync() {
    private val myProject = myEnv.project

    override fun executeSync(configurator: CommandLineDebugConfigurator?): ExecutionResult {
        val interpreter: NodeJsInterpreter = NodeJsInterpreterRef.create(this.myRunConfiguration.getPersistentData().nodeJsRef).resolveNotNull(myEnv.project)
        val commandLine = NodeCommandLineUtil.createCommandLine(if (SystemInfo.isWindows) false else null)
        var onlyFile: File? = null
        NodeCommandLineUtil.configureCommandLine(commandLine, configurator) { debugMode: Boolean -> onlyFile = this.configureCommandLine(commandLine, interpreter, debugMode) }
        val processHandler = NodeCommandLineUtil.createProcessHandler(commandLine, false)
        val consoleProperties = CypressConsoleProperties(this.myRunConfiguration, this.myEnv.executor, CypressTestLocationProvider(), NodeCommandLineUtil.shouldUseTerminalConsole(processHandler))
        val consoleView: ConsoleView = this.createSMTRunnerConsoleView(commandLine.workDirectory, consoleProperties)
        ProcessTerminatedListener.attach(processHandler)
        consoleView.attachToProcess(processHandler)
        val executionResult = DefaultExecutionResult(consoleView, processHandler)
        // todo enable restart: need cypress support for run pattern
//        executionResult.setRestartActions(consoleProperties.createRerunFailedTestsAction(consoleView))
        processHandler.addProcessListener(object: ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                onlyFile?.delete()
            }
        })
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

    private fun configureCommandLine(commandLine: GeneralCommandLine, interpreter: NodeJsInterpreter, debugMode: Boolean) : File? {
        var onlyFile: File? = null
        commandLine.charset = StandardCharsets.UTF_8
        val data = this.myRunConfiguration.getPersistentData()
        val workingDirectory = data.getWorkingDirectory()
        if (workingDirectory.isNotBlank()) {
            commandLine.withWorkDirectory(workingDirectory)
        }
        NodeCommandLineUtil.configureUsefulEnvironment(commandLine)
        NodeCommandLineUtil.prependNodeDirToPATH(commandLine, interpreter)
        commandLine.withParameters(NodePackage.findDefaultPackage(myProject, "cypress", interpreter)!!.systemDependentPath + "/bin/cypress", "run")
        if (data.additionalParams.isNotBlank()) {
            commandLine.withParameters(data.additionalParams.split("\\s+".toRegex()))
        }
        EnvironmentVariablesData.create(data.envs, data.passParentEnvs).configureCommandLine(commandLine, true)
        if (debugMode) {
        }
        commandLine.addParameter("--reporter")
        commandLine.addParameter(getCypressReporterFile().systemIndependentPath)
        when (data.kind) {
            CypressRunConfig.TestKind.DIRECTORY -> {
                commandLine.withParameters("--spec", "${FileUtil.toSystemDependentName(data.specsDir!!)}/**/*")
            }
            CypressRunConfig.TestKind.SPEC -> {
                commandLine.withParameters("--spec", data.specFile)
            }
            CypressRunConfig.TestKind.TEST -> {
                onlyFile = onlyfiSpec(data) ?: throw ExecutionException("Unable to create a .only spec to run a single test")
                commandLine.withParameters("--spec", onlyFile.systemIndependentPath)
            }
//            CypressRunConfig.TestKind.SUITE -> TODO("not implemented")
        }
        NodeCommandLineConfigurator.find(interpreter).configure(commandLine)
        return onlyFile
    }

    private fun onlyfiSpec(data: CypressRunConfig.CypressRunSettings): File? {
        val specFile = data.specFile ?: return null
        val textRange = data.textRange ?: return null
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(specFile)) ?: return null
        val doc = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null
        val text = doc.text
        val case = text.substring(textRange.startOffset, textRange.endOffset).replaceFirst(testKeyword, "${testKeyword}.only")
        val only = text.substring(0, textRange.startOffset) + case + text.substring(textRange.endOffset)
        val orig = File(specFile)
        try {
            val ext = FileUtilRt.getExtension(specFile)
            val onlyFile = File(orig.parent, "__only." + ext)
            onlyFile.deleteOnExit()
            onlyFile.writeBytes(only.toByteArray())
            return File(onlyFile.absolutePath)
        } catch (e: Exception) {
            logger<CypressRunState>().error("failed to write the 'only' spec", e)
            return null
        }
    }

    private fun getCypressReporterFile(): NodePackage {
        val contextFile = getContextFile() ?: throw ExecutionException("no test base context file defined")
        val info = NodeModuleSearchUtil.resolveModuleFromNodeModulesDir(contextFile, "cypress-intellij-reporter", NodeModuleDirectorySearchProcessor.PROCESSOR)
        if (info != null && info.moduleSourceRoot.isDirectory) {
            return NodePackage(info.moduleSourceRoot.path)
        }
        throw ExecutionException("'cypress-intellij-reporter' package not found, please install it locally")
    }

    private fun getContextFile(): VirtualFile? {
        val data = myRunConfiguration.getPersistentData()
        return findFile(data.specFile ?: "")
                ?: findFile(data.specsDir ?: "")
                ?: findFile(data.workingDirectory ?: "")
    }

    private fun findFile(path: String): VirtualFile? =
            if (FileUtil.isAbsolute(path)) LocalFileSystem.getInstance().findFileByPath(path) else null
}
