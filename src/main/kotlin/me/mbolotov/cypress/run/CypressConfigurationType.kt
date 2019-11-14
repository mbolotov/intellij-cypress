package me.mbolotov.cypress.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import me.mbolotov.cypress.CypressIcons
import javax.swing.Icon

val type = ConfigurationTypeUtil.findConfigurationType(CypressConfigurationType::class.java)

class CypressConfigurationType : ConfigurationTypeBase("CypressConfigurationType", "Cypress", "Run Cypress Test", CypressIcons.CYPRESS) {
    val configurationFactory: ConfigurationFactory

    init {
        configurationFactory = object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(p0: Project): RunConfiguration {
                return CypressRunConfig(p0, this)
            }

            override fun getIcon(): Icon {
                return CypressIcons.CYPRESS
            }

            override fun isApplicable(project: Project): Boolean {
                return true
            }
        }
        addFactory(configurationFactory)
    }
}

                                                                            