package me.mbolotov.cypress.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.javascript.nodejs.*
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.npm.NpmUtil
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageRef
import com.intellij.javascript.testFramework.interfaces.mochaTdd.MochaTddFileStructureBuilder
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

private val reporterPackage = "cypress-intellij-reporter"

class CypressRunState(private val myEnv: ExecutionEnvironment, private val myRunConfiguration: CypressRunConfig) :
    RunProfileState {
    private val testKeywords = listOf("it", "specify", "describe", "context")
    private val onlyKeywordRegex = "^(${testKeywords.joinToString("|")})\\.only$".toRegex()

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        try {
            val interpreter: NodeJsInterpreter =
                NodeJsInterpreterRef.create(this.myRunConfiguration.getPersistentData().nodeJsRef)
                    .resolveNotNull(myEnv.project)
            val commandLine = NodeCommandLineUtil.createCommandLine(if (SystemInfo.isWindows) false else null)
            val reporter =
                if (myRunConfiguration.getPersistentData().interactive) null else myRunConfiguration.getCypressReporterFile()
            var onlyElement = this.configureCommandLine(commandLine, interpreter, reporter)
            val processHandler = NodeCommandLineUtil.createProcessHandler(commandLine, false)
            val consoleProperties = CypressConsoleProperties(
                this.myRunConfiguration,
                this.myEnv.executor,
                CypressTestLocationProvider(),
                NodeCommandLineUtil.shouldUseTerminalConsole(processHandler)
            )
            val consoleView: ConsoleView = if (reporter != null) this.createSMTRunnerConsoleView(
                commandLine.workDirectory,
                consoleProperties
            ) else ConsoleViewImpl(myProject, false)
            ProcessTerminatedListener.attach(processHandler)
            consoleView.attachToProcess(processHandler)
            val executionResult = DefaultExecutionResult(consoleView, processHandler)
            // todo enable restart: need cypress support for run pattern
//        executionResult.setRestartActions(consoleProperties.createRerunFailedTestsAction(consoleView))
            processHandler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    onlyElement?.let { keywordElement ->
                        ApplicationManager.getApplication().invokeLater {
                            findTestByRange(
                                keywordElement.containingFile as JSFile,
                                keywordElement.textRange
                            )?.testElement?.children?.first()?.let {
                                val text = it.text
                                if (text.matches(onlyKeywordRegex)) {
                                    val new =
                                        JSPsiElementFactory.createJSExpression(text.replace(".only", ""), it.parent)
                                    WriteCommandAction.writeCommandAction(myProject)
                                        .shouldRecordActionForActiveDocument(false).run<Throwable> {
                                        it.replace(new)
                                        FileDocumentManager.getInstance().saveAllDocuments()
                                    }
                                }
                            }
                        }
                    }
                }
            })
            return executionResult
        } catch (e: Exception) {
            logger<CypressRunState>().error("Failed to run Cypress configuration", e)
            throw e
        }

    }

    private val myProject = myEnv.project

    private fun createSMTRunnerConsoleView(
        workingDirectory: File?,
        consoleProperties: CypressConsoleProperties
    ): ConsoleView {
        val consoleView = SMTestRunnerConnectionUtil.createConsole(
            consoleProperties.testFrameworkName,
            consoleProperties
        ) as SMTRunnerConsoleView
        consoleProperties.addStackTraceFilter(NodeStackTraceFilter(this.myProject, workingDirectory))
        consoleProperties.stackTrackFilters.forEach { consoleView.addMessageFilter(it) }
        consoleView.addMessageFilter(NodeConsoleAdditionalFilter(this.myProject, workingDirectory))
        Disposer.register(this.myProject, consoleView)
        return consoleView
    }

    private fun configureCommandLine(
        commandLine: GeneralCommandLine,
        interpreter: NodeJsInterpreter,
        reporter: String?
    ): PsiElement? {
        var onlyFile: PsiElement? = null
        commandLine.charset = StandardCharsets.UTF_8
        val data = this.myRunConfiguration.getPersistentData()
        val interactive = data.interactive

        val workingDirectory = data.getWorkingDirectory()
        if (workingDirectory.isNotBlank()) {
            commandLine.withWorkDirectory(workingDirectory)
        }
        NodeCommandLineUtil.configureUsefulEnvironment(commandLine)
        val startCmd = if (interactive) "open" else "run"
        data.npmRef
            .takeIf { it?.isNotEmpty() ?: false }
            ?.let { NpmUtil.resolveRef(NodePackageRef.create(it), myProject, interpreter) }
            ?.let { pkg ->
                val yarn = NpmUtil.isYarnAlikePackage(pkg)
                val validNpmCliJsFilePath = NpmUtil.getValidNpmCliJsFilePath(pkg)
                if (yarn) {
                    commandLine.withParameters(validNpmCliJsFilePath, "run")
                } else {
                    commandLine.withParameters(validNpmCliJsFilePath.replace("npm-cli", "npx-cli"))
                }
                commandLine.addParameter("cypress")
            }
        // falling back and run cypress directly without package manager
            ?: commandLine.withParameters(
                NodePackage.findDefaultPackage(
                    myProject,
                    "cypress",
                    interpreter
                )!!.systemDependentPath + "/bin/cypress"
            )

        commandLine.addParameter(startCmd)
        if (data.additionalParams.isNotBlank()) {
            val params = data.additionalParams.trim().split("\\s+".toRegex()).toMutableList()
            if (interactive) {
                params.removeAll { it == "--headed" || it == "--no-exit" }
            }
            commandLine.withParameters(params)
        }
        EnvironmentVariablesData.create(data.envs, data.passParentEnvs).configureCommandLine(commandLine, true)
        reporter?.let {
            commandLine.addParameter("--reporter")
            commandLine.addParameter(it)
        }
        if (data.kind == CypressRunConfig.TestKind.TEST) {
            onlyFile = onlyfiOrDie(data)
        }
        val specParams = mutableListOf(if (interactive) "--config" else "--spec")
        val specParamGenerator = { i: String, ni: String -> if (interactive) "testFiles=**/${i}" else ni }
        specParams.add(
            when (data.kind) {
                CypressRunConfig.TestKind.DIRECTORY -> {
                    "${
                        specParamGenerator(
                            File(data.specsDir!!).name,
                            FileUtil.toSystemDependentName(data.specsDir!!)
                        )
                    }/**/*"
                }
                CypressRunConfig.TestKind.SPEC, CypressRunConfig.TestKind.TEST -> {
                    specParamGenerator(File(data.specFile!!).name, data.specFile!!)
                }
            }
        )
        commandLine.withParameters(specParams)
        NodeCommandLineConfigurator.find(interpreter).configure(commandLine)
        return onlyFile
    }

    private fun onlyfiOrDie(data: CypressRunConfig.CypressRunSettings): PsiElement {
        return onlyfiSpec(data) ?: throw ExecutionException("Unable to add a .only keyword to run a single test")
    }

    private fun onlyfiSpec(data: CypressRunConfig.CypressRunSettings): PsiElement? {
        val specFile = data.specFile ?: return null
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(specFile)) ?: return null
        val jsFile = PsiManager.getInstance(myProject).findFile(virtualFile) as? JSFile ?: return null
        val allNames = data.allNames ?: restoreFromRange(data, jsFile) ?: return null
        val suiteNames = if (allNames.size > 1) allNames.dropLast(1) else allNames
        val testName = if (allNames.size == 1) null else allNames.last()
        var testElement = JasmineFileStructureBuilder.getInstance().fetchCachedTestFileStructure(jsFile)
            .findPsiElement(suiteNames, testName)
            ?: MochaTddFileStructureBuilder.getInstance().fetchCachedTestFileStructure(jsFile)
                .findPsiElement(suiteNames, testName)
            ?: return null

        testElement = generateSequence(testElement, { it.parent }).firstOrNull { it is JSCallExpression } ?: return null
        val keywordElement = testElement.children.first()
        if (!keywordElement.text.contains(".only")) {
            val new = JSPsiElementFactory.createJSExpression("${keywordElement.text}.only", testElement)
            WriteCommandAction.writeCommandAction(myProject).shouldRecordActionForActiveDocument(false).run<Throwable> {
                keywordElement.replace(new)
                FileDocumentManager.getInstance().saveAllDocuments()
            }

        }
        return testElement
    }

    private fun restoreFromRange(data: CypressRunConfig.CypressRunSettings, jsFile: JSFile): List<String>? {
        if (data.textRange == null) return null
        val cypTextRange = data.textRange!!
        val textRange = TextRange(cypTextRange.startOffset, cypTextRange.endOffset)
        val result = JasmineFileStructureBuilder.getInstance().fetchCachedTestFileStructure(jsFile)
            .findTestElementPath(textRange)?.allNames
            ?: MochaTddFileStructureBuilder.getInstance().fetchCachedTestFileStructure(jsFile)
                .findTestElementPath(textRange)?.allNames
        if (result != null) {
            data.allNames = result
        }
        return result
    }


    private fun CypressRunConfig.getCypressReporterFile(): String {
        getContextFile()?.let {
            val info = NodeModuleSearchUtil.resolveModuleFromNodeModulesDir(
                it,
                reporterPackage,
                NodeModuleDirectorySearchProcessor.PROCESSOR
            )
            if (info != null && info.moduleSourceRoot.isDirectory) {
                return NodePackage(info.moduleSourceRoot.path).systemIndependentPath
            }
        }

        return reporter.absolutePath
    }
}


private val reporter by lazy {
    Files.createTempFile("intellij-cypress-reporter", ".js").toFile().apply {
        writeBytes(CypressRunState::class.java.getResourceAsStream("/bundle.js").readBytes())
        deleteOnExit()
    }
}
