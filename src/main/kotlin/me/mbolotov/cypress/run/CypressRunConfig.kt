package me.mbolotov.cypress.run

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.ExternalizablePath
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.javascript.nodejs.NodeModuleDirectorySearchProcessor
import com.intellij.javascript.nodejs.NodeModuleSearchUtil
import com.intellij.javascript.nodejs.interpreter.NodeInterpreterUtil
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.lang.javascript.modules.InstallNodeModuleQuickFix
import com.intellij.lang.javascript.modules.NpmPackageInstallerLight
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.XmlSerializer
import me.mbolotov.cypress.run.ui.*
import org.jdom.Element
import org.jetbrains.io.LocalFileFinder
import java.io.File
import java.util.*

class CypressRunConfig(project: Project, factory: ConfigurationFactory) : LocatableConfigurationBase<CypressConfigurationType>(project, factory, ""), CommonProgramRunConfigurationParameters {

    private val reporterPackage = "cypress-intellij-reporter"

    private var myCypressRunSettings: CypressRunSettings = CypressRunSettings()

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
        val state = CypressRunState(env, this)
        return state
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        val group = SettingsEditorGroup<CypressRunConfig>()
        group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), CypressConfigurableEditorPanel(this.project))
        return group
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(this, element)
        XmlSerializer.deserializeInto(myCypressRunSettings, element)

        EnvironmentVariablesComponent.readExternal(element, envs)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(this, element)
        XmlSerializer.serializeInto(myCypressRunSettings, element)

        EnvironmentVariablesComponent.writeExternal(element, envs)
    }

    override fun suggestedName(): String? {
        return when (myCypressRunSettings.kind) {
            TestKind.DIRECTORY -> "All Tests in ${getRelativePath(project, myCypressRunSettings.specsDir!!)}"
            TestKind.SPEC -> getRelativePath(project, myCypressRunSettings.specFile!!)
            TestKind.TEST -> StringUtil.notNullize(myCypressRunSettings.testName)
        }
    }

    override fun getActionName(): String? {
        return when (myCypressRunSettings.kind) {
            TestKind.DIRECTORY -> "All Tests in ${getLastPathComponent(myCypressRunSettings.specsDir!!)}"
            TestKind.SPEC -> getLastPathComponent(myCypressRunSettings.specFile!!)
            TestKind.TEST -> StringUtil.notNullize(myCypressRunSettings.testName)
        }
    }

    private fun getRelativePath(project: Project, path: String): String {
        val file = LocalFileFinder.findFile(path)
        if (file != null && file.isValid) {
            val root = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
            if (root != null && root.isValid) {
                val relativePath = VfsUtilCore.getRelativePath(file, root, File.separatorChar)
                if (StringUtil.isNotEmpty(relativePath)) {
                    return relativePath!!
                }
            }
        }
        return getLastPathComponent(path)
    }

    private fun getLastPathComponent(path: String): String {
        val lastIndex = path.lastIndexOf('/')
        return if (lastIndex >= 0) path.substring(lastIndex + 1) else path
    }

    fun getPersistentData(): CypressRunSettings {
        return myCypressRunSettings
    }


    fun getCypressReporterFile(): NodePackage? {
        val contextFile = getContextFile() ?: return null
        val info = NodeModuleSearchUtil.resolveModuleFromNodeModulesDir(contextFile, reporterPackage, NodeModuleDirectorySearchProcessor.PROCESSOR)
        if (info != null && info.moduleSourceRoot.isDirectory) {
            return NodePackage(info.moduleSourceRoot.path)
        }
        return null
    }

    private fun getContextFile(): VirtualFile? {
        val data = getPersistentData()
        return findFile(data.specFile ?: "")
                ?: findFile(data.specsDir ?: "")
                ?: findFile(data.workingDirectory ?: "")
    }


    private fun findFile(path: String): VirtualFile? =
            if (FileUtil.isAbsolute(path)) LocalFileSystem.getInstance().findFileByPath(path) else null


    class CypTextRange(@JvmField var startOffset: Int = 0, @JvmField var endOffset: Int = 0)

    interface TestKindViewProducer {
        fun createView(project: Project): CypressTestKindView
    }

    enum class TestKind(val myName: String) : TestKindViewProducer {
        DIRECTORY("All in &directory") {
            override fun createView(project: Project) = CypressDirectoryKindView(project)
        },
        SPEC("Spec &file") {
            override fun createView(project: Project) = CypressSpecKindView(project)
        },
        TEST("Test") {
            override fun createView(project: Project) = CypressTestView(project)
        },
//        SUITE("Suite") {
//            override fun createView(project: Project) = CypressSpecKindView(project)
//        }
    }

    override fun clone(): RunConfiguration {
        val clone = super.clone() as CypressRunConfig
        clone.myCypressRunSettings = myCypressRunSettings.clone()
        return clone
    }

    data class CypressRunSettings(val u: Unit? = null) : Cloneable {
        @JvmField
        var textRange: CypTextRange? = null

        @JvmField
        var specsDir: String? = null

        @JvmField
        var specFile: String? = null

        @JvmField
        var testName: String? = null

        @JvmField
        var workingDirectory: String? = null

        @JvmField
        var envs: MutableMap<String, String> = LinkedHashMap()

        @JvmField
        var additionalParams: String = ""

        @JvmField
        var passParentEnvs: Boolean = true

        @JvmField
        var nodeJsRef: String = NodeJsInterpreterRef.createProjectRef().referenceName

        @JvmField
        var kind: TestKind = TestKind.SPEC


        public override fun clone(): CypressRunSettings {
            try {
                val data = super.clone() as CypressRunSettings
                data.envs = LinkedHashMap(envs)
                return data
            } catch (e: CloneNotSupportedException) {
                throw RuntimeException(e)
            }
        }

        fun getWorkingDirectory(): String = ExternalizablePath.localPathValue(workingDirectory)

        fun setWorkingDirectory(value: String?) {
            workingDirectory = ExternalizablePath.urlValue(value)
        }

        fun getSpecName(): String = specFile?.let { File(it).name } ?: ""

        fun setEnvs(envs: Map<String, String>) {
            this.envs.clear()
            this.envs.putAll(envs)
        }
    }

    private val reporterFound = Key<Boolean>("cypress-intellij-reporter_found")

    override fun checkConfiguration() {
        val data = getPersistentData()
        val workingDir = data.getWorkingDirectory()
        if (!File(workingDir).exists()) {
            throw RuntimeConfigurationWarning("Working directory '$workingDir' doesn't exist")
        }

        val interpreter: NodeJsInterpreter? = NodeJsInterpreterRef.create(data.nodeJsRef).resolve(project)
        NodeInterpreterUtil.checkForRunConfiguration(interpreter)
        if ((data.kind == TestKind.SPEC || data.kind == TestKind.TEST) && data.getSpecName().isBlank()) {
            throw RuntimeConfigurationError("Cypress spec must be defined")
        }
        if (data.kind == TestKind.DIRECTORY && data.specsDir.isNullOrBlank()) {
            throw RuntimeConfigurationError("Spec directory must be defined")
        }
        if (project.getUserData(reporterFound) != true) {
            if (getCypressReporterFile() == null) {
                val context = getContextFile()
                val fix = context?.let { c ->
                    findFileUpwards(c, "node_modules")?.let { packageJson ->
                        Runnable {
                            val listener = InstallNodeModuleQuickFix.createListener(project, packageJson, reporterPackage)
                            val installerLight = ServiceManager.getService(NpmPackageInstallerLight::class.java) as NpmPackageInstallerLight
                            installerLight.installPackage(project, interpreter!!, reporterPackage, null as String?, File(packageJson.path), listener, null)
                        }
                    }
                }
                throw RuntimeConfigurationWarning("Package '${reporterPackage}' not found under the Cypress project, test tab view will not be shown. Install the package to watch test execution and results", fix)
            } else {
                project.putUserData(reporterFound, true)
            }
        }
    }

    override fun getWorkingDirectory(): String? {
        return myCypressRunSettings.getWorkingDirectory()
    }

    override fun getEnvs(): MutableMap<String, String> {
        return myCypressRunSettings.envs
    }

    override fun setWorkingDirectory(value: String?) {
        myCypressRunSettings.setWorkingDirectory(value)
    }

    override fun setEnvs(envs: MutableMap<String, String>) {
        myCypressRunSettings.setEnvs(envs)
    }

    override fun isPassParentEnvs(): Boolean {
        return myCypressRunSettings.passParentEnvs
    }

    override fun setPassParentEnvs(passParentEnvs: Boolean) {
        myCypressRunSettings.passParentEnvs = passParentEnvs
    }

    override fun setProgramParameters(value: String?) {
        myCypressRunSettings.additionalParams = value ?: ""
    }

    override fun getProgramParameters(): String? {
        return myCypressRunSettings.additionalParams
    }
}
