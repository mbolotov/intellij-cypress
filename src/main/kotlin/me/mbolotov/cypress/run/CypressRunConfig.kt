package me.mbolotov.cypress.run

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.ExternalizablePath
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.*
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.javascript.nodejs.interpreter.NodeInterpreterUtil
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import me.mbolotov.cypress.run.ui.CypressConfigurableEditorPanel
import org.jdom.Element
import java.util.*

class CypressRunConfig(project: Project, factory: ConfigurationFactory) : LocatableConfigurationBase<CypressConfigurationType>(project, factory, ""), CommonProgramRunConfigurationParameters {

    private var myData: Data = Data()

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
        val state = CypressRunState(env, this)
        state.consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
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
        XmlSerializer.deserializeInto(myData, element)

        EnvironmentVariablesComponent.readExternal(element, envs)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(this, element)
        XmlSerializer.serializeInto(myData, element)

        EnvironmentVariablesComponent.writeExternal(element, envs)
    }

    fun getPersistentData(): Data {
        return myData
    }

    class CypTextRange(@JvmField var startOffset: Int = 0, @JvmField var endOffset: Int = 0)

    data class Data(val u: Unit? = null) : Cloneable {
        @JvmField
        var textRange: CypTextRange? = null
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


        public override fun clone(): Data {
            try {
                val data = super.clone() as Data
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
        if (data.specName.isNullOrBlank()) {
            throw RuntimeConfigurationError("Cypress spec must be defined")
        }
    }

    override fun getWorkingDirectory(): String? {
        return myData.getWorkingDirectory()
    }

    override fun getEnvs(): MutableMap<String, String> {
        return myData.envs
    }

    override fun setWorkingDirectory(value: String?) {
        myData.setWorkingDirectory(value)
    }

    override fun setEnvs(envs: MutableMap<String, String>) {
        myData.setEnvs(envs)
    }

    override fun isPassParentEnvs(): Boolean {
        return myData.passParentEnvs
    }

    override fun setPassParentEnvs(passParentEnvs: Boolean) {
        myData.passParentEnvs = passParentEnvs
    }

    override fun setProgramParameters(value: String?) {
        myData.additionalParams = value ?: ""
    }

    override fun getProgramParameters(): String? {
        return myData.additionalParams
    }
}
