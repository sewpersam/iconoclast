package dev.icondesigner.buildlogic.templatepack

/**
 * Icon pack intent filters. Each one was found as a string in the dex of at least one
 * of the three launchers under test, see docs/verification/m3.md. The spec 3.4 set.
 */
val ICON_PACK_ACTIONS = listOf(
    "org.adw.launcher.THEMES",
    "org.adw.ActivityStarter.THEMES",
    "com.novalauncher.THEME",
    "com.teslacoilsw.launcher.THEME",
    "com.anddoes.launcher.THEME",
    "com.dlto.atom.launcher.THEME",
)

/** The pack's one activity. Its Kotlin source lives in :template-pack. */
const val OPEN_DESIGNER_ACTIVITY = "dev.icondesigner.templatepack.OpenDesigner"

/** Manifest metadata key the activity reads to find the designer's package. */
const val DESIGNER_META_KEY = "dev.icondesigner.designer"

fun templateManifest(
    packageName: String,
    designerPackage: String,
    label: String,
    minSdk: Int,
    targetSdk: Int,
): String {
    val actions = ICON_PACK_ACTIONS.joinToString("\n") { "                <action android:name=\"$it\" />" }
    return """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${xml(packageName)}" android:versionCode="1" android:versionName="1">
    <uses-sdk android:minSdkVersion="$minSdk" android:targetSdkVersion="$targetSdk" />
    <queries>
        <package android:name="${xml(designerPackage)}" />
    </queries>
    <application
        android:label="${xml(label)}"
        android:icon="@drawable/pack_icon"
        android:allowBackup="false">
        <activity
            android:name="$OPEN_DESIGNER_ACTIVITY"
            android:exported="true"
            android:excludeFromRecents="true"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
$actions
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data android:name="$DESIGNER_META_KEY" android:value="${xml(designerPackage)}" />
        </activity>
    </application>
</manifest>
"""
}

private fun xml(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
