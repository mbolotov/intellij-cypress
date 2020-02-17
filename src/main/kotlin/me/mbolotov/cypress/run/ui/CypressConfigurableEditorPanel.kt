package me.mbolotov.cypress.run.ui

import com.google.wireless.android.sdk.stats.TestRun
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.CommonProgramParametersPanel
import com.intellij.execution.ui.MacroComboBoxWithBrowseButton
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterField
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.ui.PanelWithAnchor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.nodejs.mocha.execution.MochaTestKindView
import me.mbolotov.cypress.run.CypressRunConfig
import java.awt.*
import java.util.*
import javax.swing.*


class CypressConfigurableEditorPanel(private val myProject: Project) : SettingsEditor<CypressRunConfig>(), PanelWithAnchor {

    private lateinit var myCommonParams: CommonProgramParametersPanel
    private lateinit var myWholePanel: JPanel
    private lateinit var anchor: JComponent
    private lateinit var myNodeJsInterpreterField: NodeJsInterpreterField
    private val kindButtons: Array<JRadioButton> = Array(CypressRunConfig.TestKind.values().size) { JRadioButton(CypressRunConfig.TestKind.values()[it].myName) }
    private lateinit var kindPanel: JPanel
    private lateinit var kindSettingsPanel: JPanel

    private val directory: LabeledComponent<MacroComboBoxWithBrowseButton>
    private val myRadioButtonMap: MutableMap<CypressRunConfig.TestKind, JRadioButton> = EnumMap(CypressRunConfig.TestKind::class.java)

    private val myTestKindViewMap: MutableMap<CypressRunConfig.TestKind, CypressTestKindView> = EnumMap(CypressRunConfig.TestKind::class.java)

    private val myLongestLabelWidth: Int


    init {

        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        fileChooserDescriptor.title = ExecutionBundle.message("select.working.directory.message")
        directory = LabeledComponent.create(MacroComboBoxWithBrowseButton(fileChooserDescriptor, myProject), "Directory")

        val model = DefaultComboBoxModel<String>()
        model.addElement("All")

        val changeLists = ChangeListManager.getInstance(myProject).changeLists
        for (changeList in changeLists) {
            model.addElement(changeList.name)
        }
        kindPanel.layout = BorderLayout()
        kindPanel.add(createTestKindRadioButtonPanel())

        this.myLongestLabelWidth = JLabel("Environment variables:").preferredSize.width
    }

    private fun createTestKindRadioButtonPanel(): JPanel {
        val testKindPanel = JPanel(FlowLayout(1, JBUIScale.scale(30), 0))
        testKindPanel.border = JBUI.Borders.emptyLeft(10)
        val buttonGroup = ButtonGroup()
        CypressRunConfig.TestKind.values().forEachIndexed { index, testKind ->
            val radioButton = JRadioButton(UIUtil.removeMnemonic(testKind.myName))
            val mnemonicInd = UIUtil.getDisplayMnemonicIndex(testKind.myName)
            if (mnemonicInd != -1) {
                radioButton.setMnemonic(testKind.myName[mnemonicInd + 1])
                radioButton.displayedMnemonicIndex = mnemonicInd
            }
            radioButton.addActionListener { this.setTestKind(testKind) }
            this.myRadioButtonMap[testKind] = radioButton
            testKindPanel.add(radioButton)
            buttonGroup.add(radioButton)
        }
        return testKindPanel
    }

    private fun setTestKind(testKind: CypressRunConfig.TestKind) {
        val selectedTestKind= this.getTestKind()
        if (selectedTestKind !== testKind) {
            myRadioButtonMap[testKind]!!.isSelected = true
        }

        val view = getTestKindView(testKind)
        setCenterBorderLayoutComponent(this.kindSettingsPanel, view.getComponent())
    }

    private fun getTestKind() = myRadioButtonMap.entries.firstOrNull { it.value.isSelected }?.key

    private fun getTestKindView(testKind: CypressRunConfig.TestKind): CypressTestKindView {
        var view = this.myTestKindViewMap[testKind]
        if (view == null) {
            view = testKind.createView(myProject)
            this.myTestKindViewMap[testKind] = view
            val component = view.getComponent()
            if (component.layout is GridBagLayout) {
                component.add(Box.createHorizontalStrut(this.myLongestLabelWidth), GridBagConstraints(0, -1, 1, 1, 0.0, 0.0, 13, 0, JBUI.insetsRight(10), 0, 0))
            }
        }
        return view
    }


    private fun setCenterBorderLayoutComponent(panel: JPanel, child: Component) {
        val prevChild = (panel.layout as BorderLayout).getLayoutComponent("Center")
        if (prevChild != null) {
            panel.remove(prevChild)
        }
        panel.add(child, "Center")
        panel.revalidate()
        panel.repaint()
    }

    public override fun applyEditorTo(configuration: CypressRunConfig) {
        myCommonParams.applyTo(configuration)
        val data = configuration.getPersistentData()
        data.nodeJsRef = myNodeJsInterpreterField.interpreterRef.referenceName
        data.kind = getTestKind() ?: CypressRunConfig.TestKind.SPEC
        val view = this.getTestKindView(data.kind)
        view.applyTo(data)
    }

    public override fun resetEditorFrom(configuration: CypressRunConfig) {
        myCommonParams.reset(configuration)

        val data = configuration.getPersistentData()
        myNodeJsInterpreterField.interpreterRef = NodeJsInterpreterRef.create(data.nodeJsRef)
        setTestKind(data.kind)
        val view = this.getTestKindView(data.kind)
        view.resetFrom(data)
    }

    private fun createUIComponents() {
        myNodeJsInterpreterField = NodeJsInterpreterField(myProject, false)
    }

    override fun getAnchor(): JComponent? {
        return anchor
    }

    override fun setAnchor(anchor: JComponent?) {
        this.anchor = anchor!!
    }

    public override fun createEditor(): JComponent {
        return myWholePanel
    }

}
