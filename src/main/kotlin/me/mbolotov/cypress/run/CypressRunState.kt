package me.mbolotov.cypress.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class CypressRunState(environment: ExecutionEnvironment?, private val configuration: CypressRunConfig) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        var specFile = configuration.getPersistentData().specFile
        val textRange = configuration.getPersistentData().textRange
        var onlyFile: File? = null
        if (textRange != null && specFile != null) {
            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(configuration.getPersistentData().specFile!!))
            val doc = FileDocumentManager.getInstance().getDocument(virtualFile!!)
            if (doc != null) {
                val text = doc.text
                val case = text.substring(textRange.startOffset, textRange.endOffset).replaceFirst(testKeyword, "${testKeyword}.only")
                val only = text.substring(0, textRange.startOffset) + case + text.substring(textRange.endOffset)
                val orig = File(specFile)
                try {
                    val ext = FileUtilRt.getExtension(specFile)
                    onlyFile = File(orig.parent, "__only." + ext)
                    onlyFile.writeBytes(only.toByteArray())
                    specFile = onlyFile.absolutePath
                } catch (e: Exception) {
                    logger<CypressRunState>().error("failed to write the 'only' spec", e)
                }
            }
        }

        val interperter = NodeJsInterpreterRef.create(configuration.getPersistentData().nodeJsRef).resolveAsLocal(environment.project)
        val cmd = "${interperter.interpreterSystemIndependentPath} node_modules/cypress/bin/cypress run ${configuration.getPersistentData().additionalParams} -s ${specFile}"
        val handler = KillableProcessHandler(Runtime.getRuntime().exec(cmd, null, File(configuration.getWorkingDirectory()
                ?: ".")), cmd)
        if (textRange != null && specFile != null) {
            handler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    onlyFile?.delete()
                }
            })
        }
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}
