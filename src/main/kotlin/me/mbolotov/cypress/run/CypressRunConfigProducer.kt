package me.mbolotov.cypress.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.javascript.testFramework.interfaces.mochaTdd.MochaTddFileStructureBuilder
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder
import com.intellij.javascript.testing.JsTestRunConfigurationProducer
import com.intellij.lang.javascript.JSElementType
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ContainerUtil
import java.io.File

class CypressRunConfigProducer : JsTestRunConfigurationProducer<CypressRunConfig>(listOf("cypress")) {
    override fun isConfigurationFromCompatibleContext(configuration: CypressRunConfig, context: ConfigurationContext): Boolean {
        val funcName = configuration.getPersistentData().testName ?: return false
        val specFile = configuration.getPersistentData().specFile ?: return false
        val psiElement = context.psiLocation ?: return false
        val info = createTestElementRunInfo(psiElement, configuration.getPersistentData())?.mySettings ?: return false
        return funcName == info.testName
                && specFile == info.specFile
    }

    private fun createTestElementRunInfo(element: PsiElement, templateRunSettings: CypressRunConfig.CypressRunSettings): CypressTestElementInfo? {
        val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return null
        val textRange = element.textRange ?: return null
        val containingFile = element.containingFile as? JSFile ?: return null
        val path = JasmineFileStructureBuilder.getInstance().fetchCachedTestFileStructure(containingFile).findTestElementPath(textRange)
                ?: MochaTddFileStructureBuilder.getInstance().fetchCachedTestFileStructure(containingFile).findTestElementPath(textRange)
        if (path == null) {
            return CypressTestElementInfo(templateRunSettings, element.containingFile as? JSFile)
        }
        // todo new instance?
        templateRunSettings.specFile = virtualFile.path
        templateRunSettings.testName = path.testName
        if (templateRunSettings.workingDirectory.isNullOrBlank()) {
            templateRunSettings.setWorkingDirectory(findWorkingDir(virtualFile))
        }
        return CypressTestElementInfo(templateRunSettings, path.testElement)
    }

    class CypressTestElementInfo(val mySettings: CypressRunConfig.CypressRunSettings, val myEnclosingElement: PsiElement?)

    override fun setupConfigurationFromCompatibleContext(configuration: CypressRunConfig, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val psiElement = context.psiLocation ?: return false
        val runInfo = createTestElementRunInfo(psiElement, configuration.getPersistentData()) ?: return false
        val data = runInfo.mySettings
        configuration.setGeneratedName()
        configuration.name = "${data.getSpecName()}#${data.testName}"
        sourceElement.set(runInfo.myEnclosingElement)
        return true
    }

    private fun findWorkingDir(specName: VirtualFile): String? {
        val cyp = "cypress.json"
        var cur: File? = File(specName.path)
        while (cur != null) {
            if (cur.list { _, name -> name == cyp }?.isNotEmpty() == true) {
                return cur.absolutePath
            }
            cur = cur.parentFile
        }
        return null
    }

    override fun getConfigurationFactory(): ConfigurationFactory {
        return type.configurationFactory
    }
}

class CypressRunnable(val specName: VirtualFile, val testName: String, val textRange: TextRange?)

private fun searchFunction(sourceElement: PsiElement?): CypressRunnable? {
    val get = sourceElement
    val children = get?.parent?.nextSibling?.children ?: return null
    if (children.size < 2) return null
    val test = children[1]?.firstChild?.text ?: return null
    val file = sourceElement?.containingFile?.virtualFile ?: return null
    return CypressRunnable(file, test.substring(1).dropLast(1), get.parent.textRange)
}

private val actions = ExecutorAction.getActions(0).filter { it.toString().startsWith("Run context configuration") }.toTypedArray()

const val testKeyword = "it"

class CypressRunLineMarkerProvider : RunLineMarkerContributor() {
    override fun getInfo(e: PsiElement): Info? {
        if (e is LeafPsiElement && e.elementType is JSElementType && e.text == testKeyword && e.parent?.nextSibling?.firstChild?.text == "(") {
            return Info(AllIcons.RunConfigurations.TestState.Run,
                    { element1 -> StringUtil.join(ContainerUtil.mapNotNull<AnAction, String>(actions) { action -> getText(action, element1) }, "\n") },
                    actions)
        }
        return null
    }
}