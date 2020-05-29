package me.mbolotov.cypress.run

import com.intellij.execution.testframework.TestFrameworkRunningModel
import com.intellij.execution.testframework.TestTreeView
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import javax.swing.Icon

class ShowCypressScreenshotAction : AnAction() {
    private class MySMTRunnerTestTreeView() : SMTRunnerTestTreeView() {
        override fun getTestFrameworkRunningModel(): TestFrameworkRunningModel {
            // a canary class to detect availability of this method as it's used by reflection below
            return super.getTestFrameworkRunningModel()
        }
    }
    override fun actionPerformed(e: AnActionEvent) {
        val tree = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? SMTRunnerTestTreeView
            ?: return
        // todo find a right way to get a test properties instance
        val properties = (TestTreeView::class.java.getDeclaredMethod("getTestFrameworkRunningModel")
            .apply { isAccessible = true }.invoke(tree) as TestFrameworkRunningModel).properties
        val selectedTest = tree.selectedTest ?: return
        val project = e.project ?: return
        val testLocation = selectedTest.getLocation(project, GlobalSearchScope.everythingScope(project)).virtualFile
            ?: return
        val base = findFileUpwards(testLocation, cypressDescriptorFile)
            ?: return
        val config = CyConfig.getConfig(base, project)
        val integr = concat(base, config.integrationFolder) ?: return
        val relativeTest = VfsUtil.getRelativeLocation(testLocation, integr) ?: return
        val pic = config.screenshotsFolder
        val screenFolder = concat(concat(base, pic) ?: return, relativeTest) ?: return
        val screenshotList = screenFolder.children.filter { it.name.contains(selectedTest.name) }
        when {
            screenshotList.isEmpty() -> return
            screenshotList.size == 1 || CypressConsoleProperties.SHOW_LATEST_SCREENSHOT.get(properties) -> {
                val screenshot = screenshotList.maxBy { it.timeStamp } ?: return
                OpenFileDescriptor(project, screenshot).navigate(true)
            }
            else -> {
                JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<VirtualFile>("Select screenshot", screenshotList, null as Icon?) {
                    override fun onChosen(selectedValue: VirtualFile, finalChoice: Boolean): PopupStep<*>? {
                        OpenFileDescriptor(project, selectedValue).navigate(true)
                        return PopupStep.FINAL_CHOICE
                    }

                    override fun getTextFor(value: VirtualFile?): String {
                        return value?.name ?: ""
                    }
                }).showInCenterOf(tree)
            }
        }
    }

    private fun concat(base: VirtualFile, child: String) =
        LocalFileSystem.getInstance().findFileByIoFile(File(base.path, child))
}