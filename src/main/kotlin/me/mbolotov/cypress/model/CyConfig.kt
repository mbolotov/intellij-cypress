package me.mbolotov.cypress.run

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import kotlin.reflect.full.memberProperties

class CyConfig(
    val baseDir: VirtualFile,

    var fixturesFolder: String = "cypress/fixtures",
    var integrationFolder: String = "cypress/integration",
    var pluginsFile: String = "cypress/plugins/index.js",
    var screenshotsFolder: String = "cypress/screenshots",
    var videosFolder: String = "cypress/videos",
    var supportFile: String = "cypress/support/index.js"
) {
    companion object {
        fun getConfig(baseDir: VirtualFile, project: Project): CyConfig {
            val default = { CyConfig(baseDir) }
            val config = baseDir.findChild(cypressDescriptorFile) ?: return default()
            return CachedValuesManager.getManager(project).getCachedValue(project) {
                val res = run {
                    val parse = com.google.gson.JsonParser().parse(String(config.inputStream.readBytes()))
                    if (!parse.isJsonObject) return@run default()
                    val memberProperties = CyConfig::class.memberProperties.associateBy { it.name }

                    val res = default()
                    parse.asJsonObject.entrySet().forEach { entry ->
                        memberProperties[entry.key]?.let {
                            (it as? kotlin.reflect.KMutableProperty<*> ?: return@forEach).setter.call(res, entry.value.asString)
                        }
                    }
                    return@run res
                }
                CachedValueProvider.Result.create(res, config)
            }
        }
    }
}