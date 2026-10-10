package dev.icondesigner.templatepack

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * The pack's only activity. Launchers and dashboards that open an icon pack land here;
 * it opens the designer by explicit package and finishes. The pack ships no Kotlin
 * runtime, so this class touches only the framework: no companion, no stdlib calls.
 * The metadata key must match DESIGNER_META_KEY in build-logic's TemplateManifest.kt.
 */
class OpenDesigner : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = packageManager.getActivityInfo(componentName, PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        val designer: String? = info.metaData?.getString("dev.icondesigner.designer")
        if (designer != null) {
            val launch: Intent? = packageManager.getLaunchIntentForPackage(designer)
            if (launch != null) {
                try {
                    startActivity(launch)
                } catch (e: ActivityNotFoundException) {
                    // The designer was uninstalled. Nothing to open.
                }
            }
        }
        finish()
    }
}
