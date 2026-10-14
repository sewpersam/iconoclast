package dev.iconoclast.app.editor

import dev.iconoclast.render.BlendMode

/**
 * A control's name and its long-press help. One [Tip] per setting, used wherever that
 * setting shows, so a setting has one name everywhere. docs/naming-spec.md section 2 is
 * the source of every text here; this file is where they are reviewed.
 */
class Tip(val label: String, val help: String)

object Tips {
    // Editor shell and the layer list
    val THEME_NAME = Tip("Theme name", "Tap to rename this theme.")
    val BACK = Tip("Back to themes", "Goes back to your themes. Your changes are already saved.")
    val UNDO = Tip("Undo", "Takes back your last change.")
    val REDO = Tip("Redo", "Puts back the change you just undid.")
    val APPLY = Tip("Apply", "Saves this theme, makes it the one in use, and opens the screen that builds and installs your icon pack.")
    val ADD = Tip("Add", "Adds a new colour, image or effect on top of your icons.")
    val REORDER = Tip("Reorder", "Drag to move this layer up or down. Higher layers cover lower ones.")
    val HIDE = Tip("Hide", "Hides this layer without deleting it. Tap again to bring it back as it was.")
    val DELETE = Tip("Delete", "Deletes this layer. You can undo from the message that appears.")
    val INFO = Tip("About this page", "")

    // Page headers, the info button
    val PAGE_ICON = Tip("Icon", "The Arcticons drawing itself: its lines, the fill inside its shapes, and its size. Changes here apply to every icon in the theme.")
    val PAGE_BACKGROUND = Tip("Background", "The tile behind each icon: its shape, its colour and an optional border.")
    val PAGE_LAYER = Tip("Layer", "A colour, gradient, image or pattern painted over part of the picture. Higher layers in the list cover lower ones.")
    val PAGE_EFFECT = Tip("Effect", "An effect drawn from the icon's shape. Where it sits in the list decides what it covers and what covers it.")

    // Icon: style and lines
    val FILL_STYLE = Tip("Fill style", "Lines only draws just the icon's lines. Cut-out fills the closed shapes and cuts the lines out of them. Solid shape fills the whole icon in one colour. Solid shape with lines draws the lines over that shape.")
    val LINE_COLOUR = Tip("Line colour", "The colour of the icon's lines.")
    val LINE_THICKNESS = Tip("Line thickness", "How thick the icon's lines are. 1× is the original Arcticons thickness.")
    val LINE_ENDS = Tip("Line ends", "The shape of the open ends of lines. Flat stops exactly at the end; Square sticks out a little past it.")
    val CORNERS = Tip("Corners", "How lines look where they meet at an angle.")
    val SPIKE_LIMIT = Tip("Spike limit", "Only for Pointed corners. Very narrow corners make long spikes; a lower value flattens them off sooner.")
    val DOT_SIZE = Tip("Dot size", "Whether round dots in the icons grow and shrink with Line thickness or stay their original size.")
    val LINES_OPACITY = Tip("Lines opacity", "How see-through the lines are. 0% hides them.")
    val LINES_MIX = Tip("Lines mix mode", MIX_HELP)

    // Icon: fill
    val FILL_COLOUR = Tip("Fill colour", "The colour inside the icon's shapes.")
    val GAP_AROUND_LINES = Tip("Gap around lines", "Cut-out only. The empty space between the cut-out lines and the fill. Bigger shows more of the background through the lines.")
    val CLOSE_SMALL_GAPS = Tip("Close small gaps", "Shapes with a tiny break in their outline are treated as closed, so they still get filled. Raise it if some shapes stay empty.")
    val LINES_ONLY_BELOW = Tip("Switch to lines only below", "Cut-out only. If an icon would end up with less fill than this, it is drawn as lines only, so very thin icons stay readable.")
    val KEEP_LOOSE_LINES = Tip("Keep loose lines", "Cut-out only. Lines that do not surround anything, like antennas or dots, are still drawn on top. Off removes them.")
    val FILL_OPACITY = Tip("Fill opacity", "How see-through the fill is.")
    val FILL_MIX = Tip("Fill mix mode", MIX_HELP)

    // Icon: size and position
    val ICON_SIZE = Tip("Icon size", "How big the drawing is inside its tile. 100% reaches the edges of the tile.")
    val MOVE_X = Tip("Move left or right", "Shifts every icon sideways.")
    val MOVE_Y = Tip("Move up or down", "Shifts every icon up or down.")

    // Background
    val SHOW_BACKGROUND = Tip("Show background", "Off leaves only the icon drawing on a see-through tile.")
    val SHAPE = Tip("Shape", "The outline of the tile behind each icon. If your launcher also shapes icons, turn that off there to avoid a double edge.")
    val SQUARENESS = Tip("Squareness", "Soft square only. Low is close to a circle, high is close to a square.")
    val CORNER_ROUNDNESS = Tip("Corner roundness", "Rounded square only. 0% is a sharp square, 50% is a circle.")
    val BACKGROUND_SIZE = Tip("Background size", "How much of the tile the background shape fills. 100% touches the edges.")
    val BACKGROUND_COLOUR = Tip("Background colour", "The colour behind the icon.")
    val BACKGROUND_OPACITY = Tip("Background opacity", "How see-through the background is. 0% shows the wallpaper through it.")
    val BACKGROUND_MIX = Tip("Mix mode", MIX_HELP)
    val BORDER_WIDTH = Tip("Border width", "A line just inside the edge of the background shape. 0% means no border.")
    val BORDER_COLOUR = Tip("Border colour", "Colour of the border line.")
    val BORDER_OPACITY = Tip("Border opacity", "How see-through the border is.")

    // Added colour or image layer
    val FILL = Tip("Fill", "What this layer paints: a colour, a gradient, an image or a pattern.")
    val OPACITY = Tip("Opacity", "How see-through this layer is. 0% hides it, 100% is fully solid.")
    val SHOW_ON = Tip("Show on", "Limits this layer to one part of the picture. It only paints where that part is. Effects below paints only where the shadows, glows and strokes listed below this layer appear.")
    val INSIDE_OUTSIDE = Tip("Inside or outside", "Inside paints only on the chosen part. Outside paints everywhere except that part, for example everywhere but the icon.")
    val MIX = Tip("Mix mode", MIX_HELP)
    val IMAGE_PLACEMENT = Tip("Image placement", "Show whole image fits all of it inside the tile and may leave empty edges. Cover fills the tile and crops the rest. Repeat tiles it like wallpaper.")
    val ZOOM = Tip("Zoom", "Above 1× zooms in on the image; below 1× shrinks it.")
    val ROTATE = Tip("Rotate", "Turns the image around the centre of the tile.")
    val SAME_PART = Tip("Same part on every icon", "On: every icon shows the same middle part of the image. Off: each icon shows a different part, so they all look a little different.")
    val CUT_OUT = Tip("Cut out by brightness", "Hides this layer where its own colours are dark and keeps it where they are light. Good for turning a photo or pattern into a stencil. (Experts: a luminance matte.)")
    val EDGES = Tip("Edges", "Soft fades in from dark to light. Hard is fully on or fully off, with nothing in between.")
    val HIDDEN_BELOW = Tip("Fully hidden below", "Soft edges only. Parts darker than this are completely hidden.")
    val SHOWN_ABOVE = Tip("Fully shown above", "Soft edges only. Parts lighter than this are completely shown.")
    val CUT_OFF = Tip("Cut-off brightness", "Hard edges only. Lighter than this is shown, darker is hidden.")
    val KEEP = Tip("Keep", "Picks which parts stay visible: the light ones or the dark ones.")
    val SOFTEN_EDGE = Tip("Soften cut-out edge", "Blurs the edge of the cut-out so it fades instead of stopping sharply.")

    // Paint
    val COLOUR = Tip("Colour", "The colour this layer paints.")
    val GRADIENT_SHAPE = Tip("Gradient shape", "Straight runs from one side to the other. Round spreads out from a centre point.")
    val DIRECTION = Tip("Direction", "Which way the gradient runs across the tile.")
    val CENTRE_X = Tip("Centre left–right", "Moves the middle of the round gradient sideways.")
    val CENTRE_Y = Tip("Centre up–down", "Moves the middle of the round gradient up or down.")
    val GRADIENT_SIZE = Tip("Size", "How far the round gradient spreads before reaching its last colour.")
    val START_COLOUR = Tip("Start colour", "The colour the gradient begins with.")
    val END_COLOUR = Tip("End colour", "The colour the gradient ends with.")
    val CHANGE_IMAGE = Tip("Change image", "Pick a different image for this layer.")
    val IMAGE_COLOURS = Tip("Image colours", "Original keeps the image's colours. Tint makes its light parts one colour you pick. Recolour swaps the image's darks and lights for the colours you pick.")
    val TINT_COLOUR = Tip("Tint colour", "The colour the light parts of the image take.")
    val DARK_COLOUR = Tip("Dark parts", "The colour the dark parts of the image or pattern take.")
    val LIGHT_COLOUR = Tip("Light parts", "The colour the light parts of the image or pattern take.")
    val PATTERN = Tip("Pattern", "The kind of random pattern. Clouds is soft, Veins makes lines like marble, Speckle is fine random dots.")
    val DETAIL = Tip("Detail", "More adds finer and finer detail to the pattern.")
    val SHUFFLE_PATTERN = Tip("Shuffle pattern", "Makes a new random version of the same pattern.")
    val HUE = Tip("Hue", "Hue picks the colour, Intensity how strong it is, Brightness how light.")
    val INTENSITY = Tip("Intensity", "How strong the colour is. 0% is grey.")
    val BRIGHTNESS = Tip("Brightness", "How light the colour is. 0% is black.")
    val HEX = Tip("Hex code", "Type a colour code like FF8800. Add two more digits in front to set see-through.")

    // Effects, shared
    val EFFECT_MIX = Tip("Mix mode", MIX_HELP)
    val STRENGTH = Tip("Strength", "How strong the effect is. 0% hides it.")

    // Streaks
    val STREAK_DIRECTION = Tip("Direction", "Which way the streaks run away from the icon.")
    val STREAK_LENGTH = Tip("Length", "How far the streaks reach.")
    val FADE = Tip("Fade", "Long tail fades fast then leaves a faint trail. Even fades steadily to nothing at the full length.")
    val STREAK_COLOUR_FROM = Tip("Colour from", "Paint the streaks one colour, or take their colours from one of your image or pattern layers.")
    val STREAK_COLOUR = Tip("Streak colour", "The colour of the streaks.")
    val BROKEN_STREAKS = Tip("Broken streaks", "On breaks the smooth streaks into rough brush-like marks. Off gives one smooth smear.")
    val MARK_PATTERN = Tip("Mark pattern", "The pattern used to break up the streaks.")
    val MARK_SIZE = Tip("Mark size", "Bigger makes the marks larger and fewer.")
    val MARK_LENGTH = Tip("Mark length", "How long each mark is stretched along the streak.")
    val COVERAGE = Tip("Coverage", "More lets more of the streak through; less leaves more gaps.")
    val MARK_EDGES = Tip("Mark edges", "Low gives soft, blended marks. High gives crisp, hard marks.")
    val SHUFFLE_MARKS = Tip("Shuffle marks", "Makes a new random version of the marks.")

    // Brush strokes
    val STROKE_COUNT = Tip("Number of strokes", "How many strokes flick off each icon.")
    val STROKE_LENGTH = Tip("Stroke length", "How far each stroke reaches from the icon.")
    val STROKE_WIDTH = Tip("Stroke width", "How wide each stroke is where it starts.")
    val STROKE_SOFTNESS = Tip("Softness", "Low gives crisp edges and bristle marks; high gives soft, smooth strokes.")
    val BLUR_TIP = Tip("Blur toward the tip", "How much strokes blur as they move away from the icon.")
    val PAINT_RUNS_OUT = Tip("Paint runs out", "How much a stroke fades along its length, like a brush running dry.")
    val DRY_BRUSH = Tip("Dry brush marks", "Adds the streaky lines a dry brush leaves, thinning out toward the tip.")
    val VARIETY = Tip("Variety", "How different the strokes are from one another in direction, length, width and shade.")
    val CURL = Tip("Curl", "Strokes curve one way or the other as they travel. 0 keeps them straight.")
    val LEAN = Tip("Lean", "How much all strokes lean toward one direction instead of straight out from the icon.")
    val LEAN_DIRECTION = Tip("Lean direction", "The direction strokes lean toward. Only matters when Lean is above 0.")
    val STROKE_COLOUR_FROM = Tip("Colour from", "Paint the strokes one colour, or take their colours from one of your image or pattern layers. Each stroke takes the colour where it starts.")
    val STROKE_COLOUR = Tip("Stroke colour", "The colour of the strokes.")
    val SHUFFLE_STROKES = Tip("Shuffle strokes", "A new random arrangement of strokes.")

    // Raised edge
    val RAISED = Tip("Raised", "Inside rounds off the icon inside its outline. Across makes it stand up out of the background.")
    val RAISED_LIGHT = Tip("Light direction", "Where the light comes from. The lit side faces it.")
    val HEIGHT = Tip("Height", "How steep the raised edge looks.")
    val EDGE_WIDTH = Tip("Edge width", "How wide the raised band along the edge is.")
    val ROUNDNESS = Tip("Roundness", "Rounds the raised edge so it looks smooth instead of chiselled.")
    val LIT_COLOUR = Tip("Lit side colour", "Colour of the side facing the light. Its see-through amount sets how strong it is.")
    val LIT_MIX = Tip("Lit side mix mode", MIX_HELP)
    val SHADED_COLOUR = Tip("Shaded side colour", "Colour of the side away from the light.")
    val SHADED_MIX = Tip("Shaded side mix mode", MIX_HELP)

    // Shadows and glows
    val SHADOW_LIGHT = Tip("Light direction", "Where the light comes from. The shadow falls the other way.")
    val DISTANCE = Tip("Distance", "How far the shadow sits from the icon.")
    val SHADOW_SOFTNESS = Tip("Softness", "How blurry the shadow's edge is.")
    val SHADOW_COLOUR = Tip("Shadow colour", "The colour of the shadow.")
    val GLOW_SIZE = Tip("Glow size", "How far the glow reaches from the edge.")
    val GLOW_SOLIDITY = Tip("Glow solidity", "Low is a soft haze. High stays solid further out before fading.")
    val GLOW_COLOUR = Tip("Glow colour", "The colour of the glow.")

    // Film grain
    val GRAIN_AMOUNT = Tip("Amount", "How strong the grain is. Heavy grain makes the icon pack larger.")
    val GRAIN_SIZE = Tip("Grain size", "Size of each speck. 1 is the finest.")
    val SHUFFLE_GRAIN = Tip("Shuffle grain", "A new random grain pattern.")

    // This icon only
    val ICON_LINE_THICKNESS = Tip(LINE_THICKNESS.label, "Line thickness for this icon only. 1× is the original Arcticons thickness.")
    val ICON_FILL_STYLE = Tip(FILL_STYLE.label, "Fill style for this icon only. Same choices as the Icon page.")
    val ICON_CLOSE_GAPS = Tip(CLOSE_SMALL_GAPS.label, "Shapes with a tiny break in their outline are treated as closed, for this icon only.")
    val PATTERN_X = Tip("Pattern position left–right", "Moves which part of your images and patterns this icon shows. Grain and brush strokes shift with it too.")
    val PATTERN_Y = Tip("Pattern position up–down", "Moves which part of your images and patterns this icon shows, up and down. Grain and brush strokes shift with it too.")
    val HIDE_STREAKS = Tip("Hide streaks and brush strokes", "Leaves streaks and brush strokes off this icon only. Useful when they clash with its shape.")
    val USE_OWN_DRAWING = Tip("Use own drawing", "Goes back to this app's own Arcticons drawing.")
    val DIFFERENT_DRAWING = Tip("Use a different drawing", "Search all Arcticons drawings by name and pick one to use for this app.")
    val UNDO_ICON = Tip("Undo all changes to this icon", "Puts this icon back to match the rest of the theme.")
    val CHANGE_ICON = Tip("Change this icon only", "Tap any icon to change it on its own, without touching the rest of the theme.")
}

private const val MIX_HELP =
    "How this layer mixes with what is under it. Normal just covers it. The others darken, lighten or tint what is below; try them and watch the preview."

/** Each mix mode's plain second line, docs/naming-spec.md 2.14. */
val BLEND_LINES: Map<BlendMode, String> = mapOf(
    BlendMode.NORMAL to "Covers what is below.",
    BlendMode.MULTIPLY to "Darkens, like stacking tinted glass.",
    BlendMode.SCREEN to "Lightens, like projecting light.",
    BlendMode.OVERLAY to "Adds contrast: darks darker, lights lighter.",
    BlendMode.DARKEN to "Keeps whichever is darker.",
    BlendMode.LIGHTEN to "Keeps whichever is lighter.",
    BlendMode.COLOR_DODGE to "Strong brightening of what is below.",
    BlendMode.COLOR_BURN to "Strong darkening of what is below.",
    BlendMode.HARD_LIGHT to "Strong contrast, led by this layer.",
    BlendMode.SOFT_LIGHT to "Gentle contrast.",
    BlendMode.DIFFERENCE to "Inverts where colours differ.",
    BlendMode.EXCLUSION to "Softer version of Difference.",
    BlendMode.HUE to "Takes only this layer's colour, keeps the shading below.",
    BlendMode.SATURATION to "Takes only this layer's colour strength.",
    BlendMode.COLOR to "Tints what is below with this layer's colour.",
    BlendMode.LUMINOSITY to "Takes only this layer's lightness.",
    BlendMode.ADD to "Adds light; can blow out to white.",
)
