package me.mbolotov.cypress.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.javascript.testFramework.interfaces.mochaTdd.MochaTddFileStructureBuilder
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder
import com.intellij.javascript.testing.JsTestRunConfigurationProducer
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.text.nullize
import kotlin.reflect.KProperty1

const val cypressDescriptorFile = "cypress.json"

class CypressRunConfigProducer : JsTestRunConfigurationProducer<CypressRunConfig>(listOf("cypress")) {
    override fun isConfigurationFromCompatibleContext(configuration: CypressRunConfig, context: ConfigurationContext): Boolean {
        val psiElement = context.psiLocation ?: return false
        val cypressBase = findFileUpwards((psiElement as? PsiDirectory)?.virtualFile ?: psiElement.containingFile?.virtualFile ?: return false, cypressDescriptorFile) ?: return false
        val thatData = configuration.getPersistentData()
        val thisData = createTestElementRunInfo(psiElement, CypressRunConfig.CypressRunSettings(), cypressBase.path)?.mySettings
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

        val path = findTestByRange(containingFile, textRange)
        if (path == null) {
            templateRunSettings.kind = CypressRunConfig.TestKind.SPEC
            templateRunSettings.specFile = containingFile.virtualFile.canonicalPath
            return CypressTestElementInfo(templateRunSettings, containingFile)
        }
        templateRunSettings.specFile = virtualFile.path
        templateRunSettings.kind = if (path.testName != null || path.suiteNames.isNotEmpty() ) CypressRunConfig.TestKind.TEST else CypressRunConfig.TestKind.SPEC
        templateRunSettings.allNames = path.allNames
        if (templateRunSettings.kind == CypressRunConfig.TestKind.TEST) {
            templateRunSettings.testName = path.testName ?: path.suiteNames.last()
        }
        return CypressTestElementInfo(templateRunSettings, path.testElement)
    }

    class CypressTestElementInfo(val mySettings: CypressRunConfig.CypressRunSettings, val myEnclosingElement: PsiElement?)

    override fun setupConfigurationFromCompatibleContext(configuration: CypressRunConfig, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val psiElement = context.psiLocation ?: return false
        val cypressBase = findFileUpwards((psiElement as? PsiDirectory)?.virtualFile ?: psiElement.containingFile?.virtualFile ?: return false, cypressDescriptorFile) ?: return false
        val runInfo = createTestElementRunInfo(psiElement, configuration.getPersistentData(), cypressBase.path) ?: return false
        configuration.setGeneratedName()
        runInfo.myEnclosingElement?.let { sourceElement.set(it) }
        return true
    }

    override fun getConfigurationFactory(): ConfigurationFactory {
        return type.configurationFactory
    }
}

fun findFileUpwards(specName: VirtualFile, fileName: String): VirtualFile? {
    var cur = specName.parent
    while (cur != null) {
        if (cur.children.find {name -> name.name == fileName } != null) {
            return cur
        }
        cur = cur.parent
    }
    return null
}

fun findTestByRange(containingFile: JSFile, textRange: TextRange) =
            (JasmineFileStructureBuilder.getInstance().fetchCachedTestFileStructure(containingFile).findTestElementPath(textRange)
                    ?: MochaTddFileStructureBuilder.getInstance().fetchCachedTestFileStructure(containingFile).findTestElementPath(textRange))
