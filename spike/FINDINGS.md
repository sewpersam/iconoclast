# Spike findings, 2026-10-10

Question: can an on-phone app generate, sign and install an icon pack that
real launchers use? Answer: yes. Every step below was verified on an emulator.

## Results

| Step | Result | Evidence |
|---|---|---|
| Template pack built with aapt2 is detected as an icon pack | yes | `cmd package query-activities -a org.adw.launcher.THEMES` lists it |
| Zip rewrite of slot PNGs and assets appfilter, then align and sign, installs over the old pack | yes | md5 of installed base.apk matched |
| apksig signing runs on the Android runtime | yes | `ondevice/`, signed through app_process, then `pm install` gave Success |
| Neo 0.9.3 applies pack, res/xml appfilter | yes | `shots/c_neo_xml_plain.png` |
| Neo applies pack, assets-only appfilter | yes | `shots/neo_seq.png`, row 1 |
| Lawnchair 15b3, both appfilter variants | yes | `shots/lc_seq.png`, rows 1 and 2 |
| Kvaesitso 1.41.0, both appfilter variants | yes | `shots/c_kv1.png`, `shots/c_kv4.png` |
| Live refresh after updating the pack | **no**, all three | `shots/lc_seq.png` row 3, `shots/c_kv2.png`, `shots/c1.png` |
| Refresh after restarting the launcher | yes, all three | `shots/c2.png`, `shots/c_lc_restart.png`, `shots/c_kv3.png` |
| Neo after versionCode bump, reselect, toggle, or uninstall plus reinstall | still stale | `shots/c3.png` through `c_neo_xml_reinstall_plain.png` |
| Invert fill on real Arcticons | works, lone lines drop | `out/montage_jade.png` vs `out/montage_plain.png` |

Restart options the launchers ship: Neo has "Restart", Lawnchair has
"Restart Lawnchair". Kvaesitso has none found. Use Force stop from App info.

## Not tested

- GrapheneOS itself. No emulator image exists. Installing from an app works
  through the standard "Install unknown apps" permission there.
- The PackageInstaller session flow from inside an app. It's the standard
  installer path that F-Droid clients use.
- Nova, Smart Launcher and other proprietary launchers.

## Files

- `pack.py`: template build and on-device-style restyle.
- `render.py`: plain style, plus jade style with invert fill, texture, streaks
  and bevel.
- `ui.sh`: adb screenshot, text dump and tap-by-text helper.
- `components.txt`: the 8 emulator apps mapped to slots.

## Emulator on this machine

Only `-gpu host` works. swiftshader crashes inside its JIT thread, in
`lib64/gles_swiftshader/libGLESv2.so`, on emulator 37.1.11, 37.2.12 and
37.3.3, after the Fedora 44 upgrade. The emulator is now 37.3.3, from the
canary channel.
