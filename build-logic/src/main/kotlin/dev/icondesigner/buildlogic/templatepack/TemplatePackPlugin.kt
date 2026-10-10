package dev.icondesigner.buildlogic.templatepack

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Puts [BuildTemplatePackTask] on the applying build script's classpath. */
class TemplatePackPlugin : Plugin<Project> {
    override fun apply(project: Project) = Unit
}
