// The icon pack APK template. The designer rewrites its icons and re-signs it on the phone.
// Built with aapt2 and d8 from build-tools, as spike/pack.py proved, not by AGP.
import dev.iconoclast.buildlogic.templatepack.BuildTemplatePackTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("iconoclast.templatepack")
}

val sdkDir: File = Properties().run {
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use { load(it) }
    File(getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: error("Set sdk.dir in local.properties or ANDROID_HOME"))
}
val buildTools = sdkDir.resolve("build-tools/${libs.versions.buildTools.get()}")
val compileSdk = libs.versions.compileSdk.get()
val platformJar = listOf("android-$compileSdk", "android-$compileSdk.0")
    .map { sdkDir.resolve("platforms/$it/android.jar") }
    .firstOrNull { it.exists() } ?: error("No android.jar for compileSdk $compileSdk under $sdkDir/platforms")

dependencies {
    compileOnly(files(platformJar))
}

java.targetCompatibility = JavaVersion.VERSION_17
kotlin.compilerOptions {
    jvmTarget = JvmTarget.JVM_17
    // No Intrinsics calls: the pack does not ship the Kotlin runtime.
    freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
}

/** The designer's app name, from its one definition in :app's strings.xml. */
val designerAppName: String = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
    .parse(rootProject.file("app/src/main/res/values/strings.xml"))
    .getElementsByTagName("string").let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .first { it.getAttribute("name") == "app_name" }.textContent
    }

fun BuildTemplatePackTask.configure(name: String) {
    group = "build"
    aapt2 = buildTools.resolve("aapt2")
    d8 = buildTools.resolve("d8")
    androidJar = platformJar
    activityClasses.from(sourceSets.main.map { it.output.classesDirs })
    ccLicense = rootProject.layout.projectDirectory.file("licenses/CC-BY-SA-4.0.txt")
    packageName = providers.gradleProperty("iconoclast.packApplicationId")
    designerPackage = providers.gradleProperty("iconoclast.applicationId")
    appName = designerAppName
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    // Spec 3.4: 1024 slots. A heavily loaded phone has 300 to 500 launchable apps; the
    // empty template costs about 100 bytes per slot. Above 1024 the designer shows an error.
    slotCount = 1024
    workDir = layout.buildDirectory.dir("$name-work")
    outputApk = layout.buildDirectory.file("$name/template.apk")
}

val buildTemplatePack = tasks.register<BuildTemplatePackTask>("buildTemplatePack") {
    description = "Builds the unsigned icon pack template APK the designer ships in its assets."
    configure("template")
}

// A second, independent build of the same template. The test asserts the two are identical.
val buildTemplatePackAgain = tasks.register<BuildTemplatePackTask>("buildTemplatePackAgain") {
    description = "Builds the template a second time, for the reproducibility test."
    configure("template-again")
    outputs.upToDateWhen { false }
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    inputs.file(buildTemplatePack.flatMap { it.outputApk })
    inputs.file(buildTemplatePackAgain.flatMap { it.outputApk })
    systemProperty("template.apk", buildTemplatePack.get().outputApk.get().asFile.path)
    systemProperty("template.again.apk", buildTemplatePackAgain.get().outputApk.get().asFile.path)
    systemProperty("template.linked.apk", buildTemplatePack.get().workDir.get().file("linked.apk").asFile.path)
}
