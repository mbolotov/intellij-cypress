package me.mbolotov.cypress.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.javascript.testFramework.JsTestFileByTestNameIndex
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder
import com.intellij.javascript.testFramework.util.EscapeUtils
import com.intellij.javascript.testFramework.util.JsTestFqn
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSTestFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil

class CypressTestLocationProvider : SMTestLocator {
    private val SUITE_PROTOCOL_ID = "suite"
    private val TEST_PROTOCOL_ID = "test"
    private val SPLIT_CHAR = '.'


    override fun getLocation(protocol: String, path: String, metaInfo: String?, project: Project, scope: GlobalSearchScope): List<Location<*>> {
        val suite = SUITE_PROTOCOL_ID == protocol
        return if (!suite && TEST_PROTOCOL_ID != protocol) {
            emptyList()
        } else {
            val location = this.getTestLocation(project, path, metaInfo, suite)
            return ContainerUtil.createMaybeSingletonList(location)
        }
    }

    override fun getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): List<Location<*>> {
        throw IllegalStateException("Should not be called")
    }

    private fun getTestLocation(project: Project, locationData: String, testFilePath: String?, isSuite: Boolean): Location<*>? {
        val path = EscapeUtils.split(locationData, SPLIT_CHAR).ifEmpty { return null }
        val psiElement: PsiElement? = findJasmineElement(project, path, testFilePath, isSuite)
        return if (psiElement != null) PsiLocation.fromPsiElement(psiElement) else null
    }

    private fun findJasmineElement(project: Project, location: List<String>, testFilePath: String?, suite: Boolean): PsiElement? {
        val executedFile = findFile(testFilePath)
        val scope = GlobalSearchScope.projectScope(project)
        val testFqn = JsTestFqn(JSTestFileType.JASMINE, location)
        val jsTestVirtualFiles = JsTestFileByTestNameIndex.findFiles(testFqn, scope, executedFile)

        return jsTestVirtualFiles
                .mapNotNull { PsiManager.getInstance(project).findFile(it) as? JSFile }
                .mapNotNull {
                    val structure = JasmineFileStructureBuilder.getInstance().fetchCachedTestFileStructure(it)
                    val testName = if (suite) null else ContainerUtil.getLastItem(location) as String
                    return@mapNotNull structure.findPsiElement(testFqn.names, testName)
                }
                .first { it.isValid }
    }

    private fun findFile(filePath: String?): VirtualFile? {
        return if (StringUtil.isEmptyOrSpaces(filePath)) null else LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(filePath!!))
    }
}