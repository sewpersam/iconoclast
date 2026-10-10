// Build-time tooling: the SVG-to-bundle converter, the appfilter compiler, the pack template.
plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

// The bundle and component table formats are shared with :render, which reads them.
sourceSets.main {
    kotlin.srcDir("../render/src/format/kotlin")
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}

gradlePlugin {
    plugins {
        create("arcticons") {
            id = "iconoclast.arcticons"
            implementationClass = "dev.iconoclast.buildlogic.arcticons.ArcticonsPlugin"
        }
        create("templatepack") {
            id = "iconoclast.templatepack"
            implementationClass = "dev.iconoclast.buildlogic.templatepack.TemplatePackPlugin"
        }
    }
}
