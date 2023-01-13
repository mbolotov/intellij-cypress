package me.mbolotov.cypress.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "me.mbolotov.cypress.base.settings.CypressSettings", storages = [Storage("other.xml")])
class CypressSettings : PersistentStateComponent<CypressSettings> {
    var lastDonatBalloon: Long? = null

    override fun getState(): CypressSettings {
        return this
    }

    override fun loadState(`object`: CypressSettings) {
        XmlSerializerUtil.copyBean(`object`, this)
    }
}

fun Project.cySettings() = getService(CypressSettings::class.java)
