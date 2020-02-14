package me.mbolotov.cypress.run

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.ExternalizablePath
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.javascript.nodejs.interpreter.NodeInterpreterUtil
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.nodejs.mocha.execution.MochaTestKindView
import me.mbolotov.cypress.run.ui.CypressConfigurableEditorPanel
import me.mbolotov.cypress.run.ui.CypressDirectoryKindView
import me.mbolotov.cypress.run.ui.CypressSpecKindView
import me.mbolotov.cypress.run.ui.CypressTestKindView
import org.jdom.Element
import java.util.*

class CypressRunConfig(project: Project, factory: ConfigurationFactory) : LocatableConfigurationBase<CypressConfigurationType>(project, factory, ""), CommonProgramRunConfigurationParameters {

    private var myCypressRunSettings: CypressRunSettings = CypressRunSettings()

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
        val state = CypressRunState(env, this)
        return state
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        val group = SettingsEditorGroup<CypressRunConfig>()
        group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title", *arrayOfNulls(0)), CypressConfigurableEditorPanel(this.project))
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

    fun getPersistentData(): CypressRunSettings {
        return myCypressRunSettings
    }

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
            override fun createView(project: Project) = CypressSpecKindView(project)
        },
        SUITE("Suite") {
            override fun createView(project: Project) = CypressSpecKindView(project)
        }
    }

    data class CypressRunSettings(val u: Unit? = null) : Cloneable {
        @JvmField
        var textRange: CypTextRange? = null

        @JvmField
        var specsDir: String? = null

        @JvmField
        var specName: String? = null

        @JvmField
        var specFile: String? = null

        @JvmField
        var testName: String? = null

        @JvmField
        var workingDirectory: String? = null

        @JvmField
        var envs: MutableMap<String, String> = LinkedHashMap()

        @JvmField
        var additionalParams: String = "--no-exit --headed"

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

        fun getSpecName(): String = specName ?: ""

        fun getTestName(): String = testName ?: ""

        fun setTest(test: CypressRunnable) {
            this.specName = test.specName.name
            this.specFile = test.specName.path
            this.testName = test.testName
            this.textRange = if (textRange != null) CypTextRange(test.textRange!!.startOffset, test.textRange.endOffset) else null
        }

        fun setEnvs(envs: Map<String, String>) {
            this.envs.clear()
            this.envs.putAll(envs)
        }

    }

    override fun checkConfiguration() {
        ProgramParametersUtil.checkWorkingDirectoryExist(this, project, null)
        val data = getPersistentData()
        val interpreter: NodeJsInterpreter? = NodeJsInterpreterRef.create(data.nodeJsRef).resolve(project)
        NodeInterpreterUtil.checkForRunConfiguration(interpreter)
        if (data.kind == TestKind.SPEC && data.specName.isNullOrBlank()) {
            throw RuntimeConfigurationError("Cypress spec must be defined")
        }
        if (data.kind == TestKind.DIRECTORY && data.specsDir.isNullOrBlank()) {
            throw RuntimeConfigurationError("Spec directory must be defined")
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
