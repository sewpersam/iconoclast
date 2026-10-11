package dev.iconoclast.buildlogic.licenses

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `writeDependencyLicenses` and `checkDependencyLicenses` over the release runtime
 * classpath, M7 readiness item 8. `check` runs the second.
 */
class LicensesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        fun DependencyLicensesTask.common(verifying: Boolean) {
            group = "verification"
            configuration.set("releaseRuntimeClasspath")
            verify.set(verifying)
            configurations = project.configurations
            dependencyHandler = project.dependencies
            asset.set(project.layout.projectDirectory.file("src/main/assets/licenses/dependencies.txt"))
            notices.set(project.rootProject.layout.projectDirectory.file("THIRD_PARTY_LICENSES.md"))
        }
        project.tasks.register("writeDependencyLicenses", DependencyLicensesTask::class.java) {
            it.common(false)
            it.description = "Writes the licence list of the release runtime classpath."
        }
        val check = project.tasks.register("checkDependencyLicenses", DependencyLicensesTask::class.java) {
            it.common(true)
            it.description = "Fails when the committed licence list differs from the release runtime classpath."
        }
        project.tasks.matching { it.name == "check" }.configureEach { it.dependsOn(check) }
    }
}
