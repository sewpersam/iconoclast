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
    val DONE = Tip("Done", "Closes this theme and goes back to your themes. Every change is already saved.")
    val UNDO = Tip("Undo", "Takes back your last change.")
    val REDO = Tip("Redo", "Puts back the change you just undid.")
    val APPLY = Tip("Apply", "Saves this theme, makes it the one in use, and opens the screen that builds and installs your icon pack.")
    val ADD = Tip("Add", "Adds an effect, or an overlay that paints over your icons.")
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
    val ICON_SIZE = Tip("Icon size", "How big the drawing is inside the background shape. 100% reaches the shape's edges. Shrinking the background shrinks the icon with it.")
    val MOVE_X = Tip("Move left or right", "Shifts every icon sideways.")
    val MOVE_Y = Tip("Move up or down", "Shifts every icon up or down.")

    // Background
    val SHOW_BACKGROUND = Tip("Show background", "Off leaves only the icon drawing on a see-through tile.")
    val SHAPE = Tip("Shape", "The outline of the tile behind each icon. If your launcher also shapes icons, turn that off there to avoid a double edge.")
    val SQUARENESS = Tip("Squareness", "Soft square only. Low is close to a circle, high is close to a square.")
    val CORNER_ROUNDNESS = Tip("Corner roundness", "Rounded square only. 0% is a sharp square, 50% is a circle.")
    val BACKGROUND_SIZE = Tip("Background size", "How much of the tile the background shape fills. 100% touches the edges; smaller leaves your wallpaper around it. Its gradient or image shrinks with it, and so does the icon.")
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
    val SCALE = Tip("Scale", "Above 1× zooms in on the image; below 1× shrinks it. In Cover the image always covers the whole tile, so the smallest scale allows for the random size and shift you set.")
    val ROTATE = Tip("Rotate", "Turns the image around the centre of the tile.")
    val CROP = Tip("Crop", "Pick the part of the image to use. Drag the frame to move it, drag a corner or pinch to resize it.")
    val RANDOM_SHIFT = Tip("Random shift", "How far each icon's part of the image moves away from the middle, so icons look different. 0% gives every icon the same middle part.")
    val RANDOM_ROTATION = Tip("Random rotation", "Turns the image on each icon by up to this much, either way. 0° turns none.")
    val RANDOM_SIZE = Tip("Random size", "Zooms the image on each icon in or out by a random amount, up to double or half at 100%. 0% keeps one size.")
    val SHUFFLE_PLACEMENT = Tip("Shuffle placement", "A new random arrangement: every icon gets a different part, turn and size. Reset goes back to the first one.")
    val CUT_OUT = Tip("Cut out by brightness", "Hides this layer where its own colours are dark and keeps it where they are light. Good for turning a photo or pattern into a stencil. (Experts: a luminance matte.)")
    val EDGES = Tip("Edges", "Soft fades in from dark to light. Hard is fully on or fully off, with nothing in between.")
    val HIDDEN_BELOW = Tip("Fully hidden below", "Soft edges only. Parts darker than this are completely hidden.")
    val SHOWN_ABOVE = Tip("Fully shown above", "Soft edges only. Parts lighter than this are completely shown.")
    val CUT_OFF = Tip("Cut-off brightness", "Hard edges only. Lighter than this is shown, darker is hidden.")
    val KEEP = Tip("Keep", "Picks which parts stay visible: the light ones or the dark ones.")
    val SOFTEN_EDGE = Tip("Soften cut-out edge", "Blurs the edge of the cut-out so it fades instead of stopping sharply.")

    // A fill's finish, every fill
    val SEE_THROUGH_FILL = Tip("See-through", "How much you can see through this fill. 0% is solid, 100% hides it.")
    val FROST = Tip("Frost", "Blurs what shows through this fill, and the fill itself, like frosted glass.")
    val FINISH_GRAIN = Tip("Grain", "Fine film grain on this fill only.")
    val RIM = Tip("Light rim", "A soft light along the inside of this fill's edge, like the edge of a glass pane.")
    val GLASS = Tip("Glass", "A starting point: this fill becomes see-through and frosted, with grain and a light rim. Change any of them after.")
    val CUT_THROUGH = Tip("Cut through to wallpaper", "Erases everything under this part of the tile, so your wallpaper shows through in its shape. Raise See-through to make the part itself clear. Not the Cut-out fill style, which only shapes the fill.")
    val STAY_INSIDE = Tip("Stay inside the layer below", "On: this layer paints only where the layer right below it in the list is, and follows when you drag it. Off: it covers the whole tile.")

    // Image adjust
    val IMG_BRIGHTNESS = Tip("Brightness", "Lightens or darkens the image.")
    val IMG_CONTRAST = Tip("Contrast", "Right makes darks darker and lights lighter; left flattens toward grey.")
    val IMG_SATURATION = Tip("Saturation", "Right makes colours stronger; all the way left is black and white.")
    val IMG_WARMTH = Tip("Warmth", "Right is warmer and more orange, left is cooler and bluer.")

    // Pattern
    val PATTERN_KIND = Tip("Pattern", "Noise makes clouds, smoke and veins. Cells makes stone, crackle and speckle. Waves makes bands and rings, like wood grain.")
    val PATTERN_START = Tip("Start from", "Ready settings to start from. Each just sets the sliders below; change them as you like.")
    val PATTERN_SIZE = Tip("Size", "How big the pattern's features are.")
    val ROUGHNESS = Tip("Roughness", "How strong the fine detail is next to the big shapes.")
    val DISTORTION = Tip("Distortion", "Swirls the pattern into itself, like smoke or marble.")
    val STRETCH = Tip("Stretch", "Pulls the pattern out along one direction, for grain, streaks or veins.")
    val STRETCH_ANGLE = Tip("Stretch direction", "Which way the pattern is pulled.")
    val RIDGES = Tip("Ridges", "Folds the pattern into sharp lines, like veins in marble.")
    val RANDOMNESS = Tip("Randomness", "0% lines the cells up in a grid; 100% scatters them.")
    val CELL_LOOK = Tip("Cells show", "Cells fills each cell with its own shade. Edges draws the lines between cells. Crackle shades each cell from its middle to its edge, like dried mud.")
    val EDGE_LINE = Tip("Line width", "How thick the lines between the cells are.")
    val WAVE_SHAPE = Tip("Shape", "Bands run straight across the tile. Rings spread out from the middle.")
    val WAVE_DISTORTION = Tip("Wobble", "Bends the bands or rings, like the grain in wood.")

    // Paint
    val COLOUR = Tip("Colour", "The colour this layer paints.")
    val GRADIENT_TYPE = Tip("Gradient type", "Linear runs from one side to the other. Radial spreads out in rings from a centre point. Angular sweeps once around a centre point, like a colour wheel.")
    val DIRECTION = Tip("Direction", "Which way the gradient runs across the tile.")
    val CENTRE_X = Tip("Centre left–right", "Moves the middle of the round gradient sideways.")
    val CENTRE_Y = Tip("Centre up–down", "Moves the middle of the round gradient up or down.")
    val GRADIENT_SIZE = Tip("Size", "How far the round gradient spreads before reaching its last colour.")
    val START_ANGLE = Tip("Start angle", "Angular only. Where the sweep of colours begins and ends around the centre.")
    val SWEEP_CENTRE_X = Tip("Centre left–right", "Moves the middle of the angular gradient sideways.")
    val SWEEP_CENTRE_Y = Tip("Centre up–down", "Moves the middle of the angular gradient up or down.")
    val GRADIENT_COLOURS = Tip("Colours", "Tap the bar to add a colour. Drag a marker to move it, drag it off or hold it to remove it, tap it to change its colour.")
    val DARK_TO_LIGHT = Tip("Colours, dark to light", "The colours the image or pattern takes, from its darkest parts on the left to its lightest on the right. Tap the bar to add a colour; drag a marker to move it, drag it off or hold it to remove it.")
    val STOP_COLOUR = Tip("Marker colour", "The colour of the marker picked on the bar above.")
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
    val SEE_THROUGH = Tip("See-through", "How see-through the colour is. Left is invisible, right is solid.")
    val RECENT_COLOURS = Tip("Recent colours", "Colours you picked lately, anywhere in the app. Tap one to use it again.")
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
    val PAINT_RUNS_OUT = Tip("Paint runs out", "How soon a stroke runs dry along its length.")
    val DRY_BRUSH = Tip("Dry brush marks", "The marks the bristles leave: deeper grooves in Painted, and more of the tail breaking into dry streaks.")
    val VARIETY = Tip("Variety", "How different the strokes are from one another in direction, length, width and shade.")
    val CURL = Tip("Curl", "Strokes curve one way or the other as they travel. 0 keeps them straight.")
    val LEAN = Tip("Lean", "How much all strokes lean toward one direction instead of straight out from the icon.")
    val LEAN_DIRECTION = Tip("Lean direction", "The direction strokes lean toward. Only matters when Lean is above 0.")
    val STROKE_COLOUR_FROM = Tip("Colour from", "Paint the strokes one colour, or take their colours from one of your image or pattern layers. Each stroke takes the colour where it starts.")
    val STROKE_COLOUR = Tip("Stroke colour", "The colour of the strokes.")
    val SHUFFLE_STROKES = Tip("Shuffle strokes", "A new random arrangement of strokes.")
    val STROKE_DIRECTION = Tip("Direction", "Outward from a point flicks the strokes out from the icon's edge. All one way lays them side by side in one direction, like paint swiped across behind the icon.")
    val STROKE_ORIGIN = Tip("Origin", "Where the outward strokes burst from. The middle is the icon itself; move it toward a corner or an edge. Double tap to centre.")
    val STROKE_ANGLE = Tip("Angle", "The direction all the strokes run.")
    val STROKE_STYLE = Tip("Style", "Painted looks like real paint: a thick body with bristle grooves that catch the light, a heavier start, and a tail that breaks up as the brush runs dry. Classic is the earlier, smoother look.")

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
    val SHADOW_OFFSET = Tip("Offset", "Drag the dot to where the shadow falls. The middle puts it right under the icon. Double tap to centre.")
    val SHADOW_SOFTNESS = Tip("Softness", "How blurry the shadow's edge is.")
    val SHADOW_COLOUR = Tip("Shadow colour", "The colour of the shadow.")
    val GLOW_SIZE = Tip("Glow size", "How far the glow reaches from the edge.")
    val GLOW_SOLIDITY = Tip("Glow solidity", "Low is a soft haze. High stays solid further out before fading.")
    val GLOW_COLOUR = Tip("Glow colour", "The colour of the glow.")

    // Blur
    val BLUR_AMOUNT = Tip("Blur amount", "How soft everything below this layer becomes. It blurs your own layers, like an image background; it cannot blur your wallpaper.")
    val BLUR_WHERE = Tip("Blur where", "Where the blur shows. Whole icon to blur only behind the icon, like frosted glass, or Everywhere for the whole tile.")

    // Grain
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
