package me.mbolotov.cypress

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object CypressIcons {
    val CYPRESS: Icon = IconLoader.getIcon("/icons/cypress-16x16.png", this.javaClass.classLoader)
    val SCREENSHOT: Icon = IconLoader.getIcon("/icons/screenshot-16x16.png", this.javaClass.classLoader)
}
