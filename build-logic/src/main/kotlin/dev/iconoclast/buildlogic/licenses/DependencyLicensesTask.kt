package dev.iconoclast.buildlogic.licenses

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

/**
 * Resolves [configuration], reads each artifact's licence from its POM, and writes the
 * licence asset and the THIRD_PARTY_LICENSES.md section, or with [verify] fails when either
 * committed file differs from what the classpath gives. An artifact with no licence in
 * its POM or any parent POM fails both.
 */
abstract class DependencyLicensesTask : DefaultTask() {
    @get:Input abstract val configuration: Property<String>
    @get:Input abstract val verify: Property<Boolean>
    @get:Internal abstract val asset: RegularFileProperty
    @get:Internal abstract val notices: RegularFileProperty

    /** Set by [LicensesPlugin] at configuration, so the action never reads Task.project. */
    @get:Internal lateinit var configurations: ConfigurationContainer
    @get:Internal lateinit var dependencyHandler: DependencyHandler

    init {
        notCompatibleWithConfigurationCache("resolves POMs through the dependency handler")
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun run() {
        val deps = resolve()
        val list = LicenseList.asset(deps)
        val noticesFile = notices.get().asFile
        val md = LicenseList.replaceSection(noticesFile.readText(), LicenseList.markdown(deps))
        val assetFile = asset.get().asFile
        if (verify.get()) {
            val stale = buildList {
                if (!assetFile.exists() || assetFile.readText() != list) add(assetFile.name)
                if (noticesFile.readText() != md) add(noticesFile.name)
            }
            if (stale.isNotEmpty()) throw GradleException(
                "${stale.joinToString()} do not match ${configuration.get()}. Run ./gradlew ${path.replace("check", "write")} and commit.",
            )
            logger.lifecycle("Licence list matches ${configuration.get()}: ${deps.size} artifacts")
        } else {
            assetFile.parentFile.mkdirs()
            assetFile.writeText(list)
            noticesFile.writeText(md)
            logger.lifecycle("Wrote ${deps.size} artifacts to ${assetFile.name} and ${noticesFile.name}")
        }
    }

    private fun resolve(): List<Dependency> {
        val ids = configurations.getByName(configuration.get()).incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
        val missing = ArrayList<String>()
        val deps = ids.map { id ->
            val names = licenses(id.group, id.module, id.version)
            if (names.isEmpty()) missing += id.displayName
            Dependency(id.group, id.module, id.version, names.map(LicenseList::spdx).distinct())
        }
        if (missing.isNotEmpty()) throw GradleException("No licence in the POM or its parents: ${missing.joinToString()}")
        return deps.distinctBy { it.coordinates }
    }

    /** The licence names of the first POM up the parent chain that declares any. */
    private fun licenses(group: String, module: String, version: String, depth: Int = 0): List<String> {
        val pom = LicenseList.parsePom(pomBytes(group, module, version) ?: return emptyList())
        if (pom.licenses.isNotEmpty() || depth >= MAX_PARENTS) return pom.licenses
        val (g, a, v) = pom.parent ?: return emptyList()
        return licenses(g, a, v, depth + 1)
    }

    private fun pomBytes(group: String, module: String, version: String): ByteArray? =
        dependencyHandler.createArtifactResolutionQuery()
            .forModule(group, module, version)
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .execute().resolvedComponents
            .flatMap { it.getArtifacts(MavenPomArtifact::class.java) }
            .filterIsInstance<ResolvedArtifactResult>()
            .firstOrNull()?.file?.readBytes()

    private companion object {
        const val MAX_PARENTS = 5
    }
}
