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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.nullize
import java.io.File
import kotlin.reflect.KProperty1

class CypressRunConfigProducer : JsTestRunConfigurationProducer<CypressRunConfig>(listOf("cypress")) {
    override fun isConfigurationFromCompatibleContext(configuration: CypressRunConfig, context: ConfigurationContext): Boolean {
        val psiElement = context.psiLocation ?: return false
        val cypressBase = findCypressBase((psiElement as? PsiDirectory)?.virtualFile ?: psiElement.containingFile?.virtualFile ?: return false) ?: return false
        val thatData = configuration.getPersistentData()
        val thisData = createTestElementRunInfo(psiElement, CypressRunConfig.CypressRunSettings(), cypressBase)?.mySettings
                ?: return false
        if (thatData.kind != thisData.kind) return false
        val compare: (KProperty1<CypressRunConfig.CypressRunSettings, String?>) -> Boolean = { it.get(thatData).nullize(true) == it.get(thisData).nullize(true) }
        return when (thatData.kind) {
            CypressRunConfig.TestKind.DIRECTORY -> compare(CypressRunConfig.CypressRunSettings::specsDir)
            CypressRunConfig.TestKind.SPEC -> compare(CypressRunConfig.CypressRunSettings::specFile)
            CypressRunConfig.TestKind.TEST -> compare(CypressRunConfig.CypressRunSettings::specFile) && compare(CypressRunConfig.CypressRunSettings::testName)
        }
    }

    private fun createTestElementRunInfo(element: PsiElement, templateRunSettings: CypressRunConfig.CypressRunSettings, cypressBase: String): CypressTestElementInfo? {
        val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return null
        templateRunSettings.setWorkingDirectory(cypressBase)
        val containingFile = element.containingFile as? JSFile ?: return if (virtualFile.isDirectory) {
            templateRunSettings.kind = CypressRunConfig.TestKind.DIRECTORY
            templateRunSettings.specsDir = virtualFile.canonicalPath
            return CypressTestElementInfo(templateRunSettings, null)
        } else null

        val textRange = element.textRange ?: return null

        val path = JasmineFileStructureBuilder.getInstance().fetchCachedTestFileStructure(containingFile).findTestElementPath(textRange)
                ?: MochaTddFileStructureBuilder.getInstance().fetchCachedTestFileStructure(containingFile).findTestElementPath(textRange)
        if (path == null) {
            templateRunSettings.kind = CypressRunConfig.TestKind.SPEC
            templateRunSettings.specFile = containingFile.virtualFile.canonicalPath
            return CypressTestElementInfo(templateRunSettings, containingFile)
        }
        templateRunSettings.specFile = virtualFile.path
        templateRunSettings.kind = if (path.testName != null) CypressRunConfig.TestKind.TEST else CypressRunConfig.TestKind.SPEC
        templateRunSettings.textRange = CypressRunConfig.CypTextRange(textRange.startOffset, textRange.endOffset)
        if (templateRunSettings.kind == CypressRunConfig.TestKind.TEST) {
            templateRunSettings.testName = path.testName
        }
        return CypressTestElementInfo(templateRunSettings, path.testElement)
    }

    class CypressTestElementInfo(val mySettings: CypressRunConfig.CypressRunSettings, val myEnclosingElement: PsiElement?)

    override fun setupConfigurationFromCompatibleContext(configuration: CypressRunConfig, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val psiElement = context.psiLocation ?: return false
        val cypressBase = findCypressBase((psiElement as? PsiDirectory)?.virtualFile ?: psiElement.containingFile?.virtualFile ?: return false) ?: return false
        val runInfo = createTestElementRunInfo(psiElement, configuration.getPersistentData(), cypressBase) ?: return false
        configuration.setGeneratedName()
        runInfo.myEnclosingElement?.let { sourceElement.set(it) }
        return true
    }

    private fun findCypressBase(specName: VirtualFile): String? {
        val cyp = "cypress.json"
        var cur: File? = File(specName.path).parentFile
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