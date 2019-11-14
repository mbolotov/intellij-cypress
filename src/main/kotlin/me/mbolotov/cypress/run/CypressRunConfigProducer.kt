package me.mbolotov.cypress.run

import com.google.common.base.Strings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.lang.javascript.JSElementType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.containers.ContainerUtil
import java.io.File

class CypressRunConfigProducer : LazyRunConfigurationProducer<CypressRunConfig>() {
    override fun isConfigurationFromContext(configuration: CypressRunConfig, context: ConfigurationContext): Boolean {
        val funcName = configuration.getPersistentData().testName ?: return false
        val specFile = configuration.getPersistentData().specFile ?: return false
        val psiElement = context.location?.psiElement ?: return false
        val func = searchFunction(psiElement) ?: return false
        return funcName == func.testName
                && File(specFile) == VfsUtilCore.virtualToIoFile(func.specName)
    }

    override fun setupConfigurationFromContext(configuration: CypressRunConfig, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val testDefinition = searchFunction(sourceElement.get()) ?: return false
        if (Strings.isNullOrEmpty(testDefinition.testName)) {
            return false
        }
        val data = configuration.getPersistentData()
        data.setTest(testDefinition)
        data.setWorkingDirectory(findWorkingDir(testDefinition.specName))
        configuration.setGeneratedName()
        configuration.name = "${testDefinition.specName.name}#${testDefinition.testName}"
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