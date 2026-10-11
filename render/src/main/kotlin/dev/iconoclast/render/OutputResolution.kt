package dev.iconoclast.render

/**
 * The pack's icon size, a user setting, M7 readiness item 7. Only these two are offered.
 *
 * - [FULL], 432 px: the 108 dp adaptive canvas at xxxhdpi, the default. The phone budget
 *   (Apply of 300 icons under 20 s) is stated and measured at this size.
 * - [COMPACT], 324 px, three quarters: for users short on storage. A grain look costs about
 *   330 KB an icon at 432 px; 324 px holds 56% of the pixels. It renders faster, so it
 *   stays inside the 432 px budget without its own row.
 *
 * Grain cells are 1/[px] of the canvas at grain size 1, so each pack pixel is one grain
 * cell at either size and grain looks the same; previews box-filter the cells of the
 * resolution they preview. Everything else is canvas-relative already.
 */
enum class OutputResolution(val px: Int) {
    FULL(432),
    COMPACT(324),
}
