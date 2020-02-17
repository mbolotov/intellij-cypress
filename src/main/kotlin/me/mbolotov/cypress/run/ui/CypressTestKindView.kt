package me.mbolotov.cypress.run.ui

import com.intellij.javascript.testFramework.util.TestFullNameView
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.SwingHelper
import com.intellij.webcore.ui.PathShortener
import com.jetbrains.nodejs.NodeJSBundle
import me.mbolotov.cypress.run.CypressRunConfig
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

abstract class CypressTestKindView {
    abstract fun getComponent(): JComponent

    abstract fun resetFrom(settings: CypressRunConfig.CypressRunSettings)
    abstract fun applyTo(settings: CypressRunConfig.CypressRunSettings)
}

class CypressDirectoryKindView(project: Project) : CypressTestKindView() {
    private val myTestDirTextFieldWithBrowseButton: TextFieldWithBrowseButton
    private val myPanel: JPanel

    init {
        myTestDirTextFieldWithBrowseButton = createTestDirPathTextField(project)
        PathShortener.enablePathShortening(myTestDirTextFieldWithBrowseButton.textField, null)
        myPanel = FormBuilder().setAlignLabelOnRight(false).addLabeledComponent("&Test directory:", myTestDirTextFieldWithBrowseButton).panel
    }

    private fun createTestDirPathTextField(project: Project): TextFieldWithBrowseButton {
        val textFieldWithBrowseButton = TextFieldWithBrowseButton()
        SwingHelper.installFileCompletionAndBrowseDialog(project, textFieldWithBrowseButton, NodeJSBundle.message("runConfiguration.mocha.test_dir.browse_dialog.title"), FileChooserDescriptorFactory.createSingleFolderDescriptor())
        return textFieldWithBrowseButton
    }

    override fun getComponent(): JComponent {
        return myPanel
    }

    override fun resetFrom(settings: CypressRunConfig.CypressRunSettings) {
        myTestDirTextFieldWithBrowseButton.text = FileUtil.toSystemDependentName(settings.specsDir!!)
    }

    override fun applyTo(settings: CypressRunConfig.CypressRunSettings) {
        settings.specsDir = PathShortener.getAbsolutePath(myTestDirTextFieldWithBrowseButton.textField)
    }
}

class CypressSpecKindView(project: Project) : CypressTestKindView() {
    private val myTestFileTextFieldWithBrowseButton = TextFieldWithBrowseButton()
    internal val myFormBuilder: FormBuilder

    init {
        PathShortener.enablePathShortening(this.myTestFileTextFieldWithBrowseButton.textField, null as JTextField?)
        SwingHelper.installFileCompletionAndBrowseDialog(project, this.myTestFileTextFieldWithBrowseButton, NodeJSBundle.message("runConfiguration.mocha.test_file.browse_dialog.title"), FileChooserDescriptorFactory.createSingleFileDescriptor())
        this.myFormBuilder = FormBuilder().setAlignLabelOnRight(false).addLabeledComponent("&Test file:", this.myTestFileTextFieldWithBrowseButton)
    }

    override fun getComponent(): JComponent {
        return myFormBuilder.panel
    }

    override fun resetFrom(settings: CypressRunConfig.CypressRunSettings) {
        myTestFileTextFieldWithBrowseButton.text = FileUtil.toSystemDependentName(settings.specFile!!)
    }

    override fun applyTo(settings: CypressRunConfig.CypressRunSettings) {
        settings.specFile = PathShortener.getAbsolutePath(myTestFileTextFieldWithBrowseButton.textField)
    }


}
class CypressTestView(project: Project) : CypressTestKindView() {
    private val myTestFileView: CypressSpecKindView = CypressSpecKindView(project)
    private val myTestFullNameView: TestFullNameView = TestFullNameView()
    private val myPanel: JPanel

    init {
        this.myPanel = this.myTestFileView.myFormBuilder.addLabeledComponent("Test name:", this.myTestFullNameView.component).panel
    }

    override fun getComponent(): JComponent {
        return myPanel
    }

    override fun resetFrom(settings: CypressRunConfig.CypressRunSettings) {
        this.myTestFileView.resetFrom(settings)
        this.myTestFullNameView.names = listOf(settings.testName)
    }

    override fun applyTo(settings: CypressRunConfig.CypressRunSettings) {
        this.myTestFileView.applyTo(settings)
        settings.testName = this.myTestFullNameView.names.getOrElse(0) {""}
    }

}