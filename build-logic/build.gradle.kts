// Build-time tooling: the SVG-to-bundle converter and the appfilter compiler.
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
            id = "icondesigner.arcticons"
            implementationClass = "dev.icondesigner.buildlogic.arcticons.ArcticonsPlugin"
        }
    }
}
