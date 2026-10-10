#!/usr/bin/env python3
"""Spike: prove a pre-built icon pack template can be rewritten and re-signed
after the fact, the way the designer app would do it on the phone.

build   <variant> <pkg> <out.apk>        variant: xml | assets | both
restyle <template.apk> <icons_dir> <components.txt> <out.apk> <variant>
"""
import os, shutil, subprocess, sys, zipfile
from pathlib import Path

BT = Path.home() / "Android/Sdk/build-tools/36.0.0"
ANDROID_JAR = Path.home() / "Android/Sdk/platforms/android-35/android.jar"
HERE = Path(__file__).resolve().parent
KEY = HERE / "designer.keystore"
SLOTS = 32

MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{pkg}" android:versionCode="{ver}" android:versionName="{ver}">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
  <application android:label="{label}" android:hasCode="false" android:icon="@drawable/slot_00">
    <activity android:name=".Pack" android:exported="true">
      <intent-filter>
        <action android:name="org.adw.launcher.THEMES"/>
        <action android:name="org.adw.ActivityStarter.THEMES"/>
        <action android:name="com.novalauncher.THEME"/>
        <action android:name="com.teslacoilsw.launcher.THEME"/>
        <action android:name="com.anddoes.launcher.THEME"/>
        <action android:name="com.dlto.atom.launcher.THEME"/>
        <category android:name="android.intent.category.DEFAULT"/>
      </intent-filter>
    </activity>
  </application>
</manifest>
"""


def run(*a):
    subprocess.run([str(x) for x in a], check=True)


def appfilter(components):
    rows = [f'  <item component="ComponentInfo{{{c}}}" drawable="slot_{i:02d}"/>'
            for i, c in enumerate(components)]
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(rows) + "\n</resources>\n"


def placeholder_png(path):
    run("magick", "-size", "256x256", "xc:#808080", path)


def ensure_key():
    if not KEY.exists():
        run("keytool", "-genkeypair", "-keystore", KEY, "-alias", "d", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "10000", "-storepass", "spikepass",
            "-keypass", "spikepass", "-dname", "CN=icon designer spike")


def sign(unaligned, out):
    ensure_key()
    aligned = str(out) + ".aligned"
    run(BT / "zipalign", "-f", "-p", "4", unaligned, aligned)
    run(BT / "apksigner", "sign", "--ks", KEY, "--ks-pass", "pass:spikepass",
        "--out", out, aligned)
    os.remove(aligned)


def build(variant, pkg, out, components, ver=1):
    work = HERE / f"build_{pkg}"
    shutil.rmtree(work, ignore_errors=True)
    (work / "res/drawable-nodpi").mkdir(parents=True)
    (work / "res/xml").mkdir(parents=True)
    (work / "assets").mkdir()
    proto = work / "proto.png"
    placeholder_png(proto)
    for i in range(SLOTS):
        shutil.copy(proto, work / f"res/drawable-nodpi/slot_{i:02d}.png")
    af = appfilter(components)
    if variant in ("xml", "both"):
        (work / "res/xml/appfilter.xml").write_text(af)
    if variant in ("assets", "both"):
        (work / "assets/appfilter.xml").write_text(af)
    (work / "AndroidManifest.xml").write_text(
        MANIFEST.format(pkg=pkg, ver=ver, label=f"Spike pack {variant}"))
    run(BT / "aapt2", "compile", "--dir", work / "res", "-o", work / "res.zip")
    run(BT / "aapt2", "link", "-o", work / "unsigned.apk", "-I", ANDROID_JAR,
        "--manifest", work / "AndroidManifest.xml", "-A", work / "assets",
        "--no-compress-regex", r"\.png$", work / "res.zip")
    sign(work / "unsigned.apk", out)


def restyle(template, icons_dir, components, out, variant):
    """Pure zip rewrite. No aapt, no recompiling. This is the on-phone step."""
    icons = sorted(Path(icons_dir).glob("*.png"))
    tmp = str(out) + ".tmp"
    with zipfile.ZipFile(template) as zin, zipfile.ZipFile(tmp, "w") as zout:
        names = zin.namelist()
        for info in zin.infolist():
            n = info.filename
            if n.startswith("META-INF/"):
                continue
            data = zin.read(n)
            stem = Path(n).stem
            if n.startswith("res/drawable") and stem.startswith("slot_"):
                i = int(stem[5:])
                if i < len(icons):
                    data = icons[i].read_bytes()
            if n == "assets/appfilter.xml":
                data = appfilter(components).encode()
            zi = zipfile.ZipInfo(n, info.date_time)
            zi.compress_type = info.compress_type
            zout.writestr(zi, data)
    sign(tmp, out)
    os.remove(tmp)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "build":
        variant, pkg, out, comps = sys.argv[2:6]
        ver = int(sys.argv[6]) if len(sys.argv) > 6 else 1
        build(variant, pkg, out, Path(comps).read_text().split(), ver)
    elif cmd == "restyle":
        template, icons_dir, comps, out, variant = sys.argv[2:7]
        restyle(template, icons_dir, Path(comps).read_text().split(), out, variant)
