package me.mbolotov.cypress.run.ui

import com.intellij.execution.ui.CommonProgramParametersPanel
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterField
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.ui.PanelWithAnchor
import com.intellij.ui.components.JBLabel
import me.mbolotov.cypress.run.CypressRunConfig
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


class CypressConfigurableEditorPanel(private val myProject: Project) : SettingsEditor<CypressRunConfig>(), PanelWithAnchor {

    private lateinit var mySpec: LabeledComponent<JLabel>
    private lateinit var myTest: LabeledComponent<JLabel>
    private lateinit var myCommonParams: CommonProgramParametersPanel
    private lateinit var myWholePanel: JPanel
    private lateinit var anchor: JComponent
    private lateinit var myNodeJsInterpreterField: NodeJsInterpreterField

    init {
        val model = DefaultComboBoxModel<String>()
        model.addElement("All")

        val changeLists = ChangeListManager.getInstance(myProject).changeLists
        for (changeList in changeLists) {
            model.addElement(changeList.name)
        }
    }

    public override fun applyEditorTo(configuration: CypressRunConfig) {
        myCommonParams.applyTo(configuration)
        configuration.getPersistentData().nodeJsRef = myNodeJsInterpreterField.interpreterRef.referenceName
    }

    public override fun resetEditorFrom(configuration: CypressRunConfig) {
        myCommonParams.reset(configuration)

        val data = configuration.getPersistentData()
        mySpec.component.text = data.getSpecName()
        myTest.component.text = data.getTestName()
        myNodeJsInterpreterField.interpreterRef = NodeJsInterpreterRef.create(data.nodeJsRef)
    }

    private fun createUIComponents() {
        mySpec = LabeledComponent()
        mySpec.component = JBLabel("")

        myTest = LabeledComponent()
        myTest.component = JBLabel("")

        myNodeJsInterpreterField = NodeJsInterpreterField(myProject, false)
    }

    override fun getAnchor(): JComponent? {
        return anchor
    }

    override fun setAnchor(anchor: JComponent?) {
        this.anchor = anchor!!
        mySpec.anchor = anchor
        myTest.anchor = anchor
    }

    public override fun createEditor(): JComponent {
        return myWholePanel
    }

}
