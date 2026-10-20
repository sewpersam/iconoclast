# Background size (2026-10-19)

His request: a Size control for the background shape, like the icon's Size.

## Change

- Editor: Background size sits under the shape, visible, 50% to 100% (was hidden under More options, 70% to 100%). Same slider, reset and long-press help as Icon size.
- Renderer: the background layer's own gradient or image spans the shape's square, not the canvas (`Paints.fill` and `Mappings.texture` take a frame; `StackInput.backgroundFrame`). At 100% every pixel is computed as before.
- Patterns, other layers on the background, effects, finish and border keep the canvas's scale.
- Schema 10, `MigrationV9ToV10.kt`: below 100% an old gradient or image is rewritten into the shape's frame, so it looks as before.

## Proof

| Check | Result |
|---|---|
| `./gradlew :render:test` | 163 tests, 0 failures; every existing golden byte-identical, 30 new (`bg_gradient_75/50`, `bg_image_75/50`, `bg_layers_50`) |
| `BackgroundSizeTest` | 7 tests. Gradients and images (crop, Cover, random shift and turn) at 50% match a whole render at half the canvas within 1/255; patterns and an overlay layer match the 100% render exactly inside the shape; border stays 10 px at 200 px |
| Same suite, frame forced to 1 | 2 of 7 fail: the probe sees the defect |
| `./gradlew :app:testDebugUnitTest` | 145 tests, 0 failures. `MigrationV9ToV10Test`: every schema 9 golden migrates unchanged; 70% gradients and centred images keep their look within 1/255; scale capped at 4 |
| Same migration test, migration a no-op | 2 of 4 fail |
| ART identity on icondesign36 | `compared 538 PNGs, 0 differ` |

## Pictures

- `bg-size/bg-size.png`: gradient, image (crop, random placement) and pattern with finish and border, at 100, 85, 70 and 50%. `./gradlew :render:bgSizeSheet`.
- `bg-size/editor-100.png`: Neon's Background page, Size under the shape.
- `bg-size/editor-71-gradient.png`: dragged to 71%, gradient picked; the preview shrinks.
- `bg-size/editor-50.png`, `editor-help.png`: 50%, and the long-press help. At 50% Neon's 80% icon is trimmed by the shape, as the help says.
