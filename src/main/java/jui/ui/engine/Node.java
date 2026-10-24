package jui.ui.engine;

import jui.text.TextLayout;
import jui.ui.Align;
import jui.ui.Justify;

/**
 * One element of a frame, from the window's node pool: what the view set on
 * it, and what layout made of it. Internal to the engine; its fields are
 * public so that the ui package, layout and paint share them.
 */
public final class Node {
    public static final byte BOX = 0, TEXT = 1, SPACER = 2, DIVIDER = 3;
    public static final int NONE = -1;

    // Structure.
    public long id;
    /** Unique to this node's current use: an Element made for an earlier use does not match it. */
    public long gen;
    public int parent, firstChild, lastChild, nextSibling, childCount;
    public byte kind;
    public String label;

    // Layout input. NaN is auto (or no bound); Ui.AUTO is an automatic margin.
    public boolean row, reverse;
    public float width, height, minWidth, maxWidth, minHeight, maxHeight;
    public float widthPercent, heightPercent;
    public float grow, shrink, basis, gap;
    public float padTop, padRight, padBottom, padLeft;
    public float marTop, marRight, marBottom, marLeft;
    /** The border's width, which also insets the content. */
    public float border;
    public Justify justify;
    public Align alignItems;
    /** Null follows the parent's alignItems. */
    public Align alignSelf;

    // Text input: 0 and NaN inherit.
    public String text;
    public int textColor;
    public float fontSize;
    public int fontWeight;
    public boolean singleLine;

    // Visual, colors as 0xRRGGBBAA.
    public int background, borderColor, shadowColor;
    public float radiusTL, radiusTR, radiusBR, radiusBL;
    public float shadowX, shadowY, shadowBlur, shadowSpread;

    // Layout output: DIPs from the window's top left.
    public float x, y, w, h;
    public TextLayout layout;
    public int resolvedColor;

    // Layout's scratch while it resolves this node's size in its parent.
    float flexBasis, flexTarget, crossSize, violation;
    boolean frozen;

    /** Back to the defaults, for a new use; the generation is the pool's to set. */
    public void reset() {
        id = 0;
        parent = firstChild = lastChild = nextSibling = NONE;
        childCount = 0;
        kind = BOX;
        label = null;

        row = reverse = false;
        width = height = minWidth = maxWidth = minHeight = maxHeight = Float.NaN;
        widthPercent = heightPercent = Float.NaN;
        grow = 0;
        shrink = 1;
        basis = Float.NaN;
        gap = 0;
        padTop = padRight = padBottom = padLeft = 0;
        marTop = marRight = marBottom = marLeft = 0;
        border = 0;
        justify = Justify.START;
        alignItems = Align.STRETCH;
        alignSelf = null;

        text = null;
        textColor = 0;
        fontSize = Float.NaN;
        fontWeight = 0;
        singleLine = false;

        background = borderColor = shadowColor = 0;
        radiusTL = radiusTR = radiusBR = radiusBL = 0;
        shadowX = shadowY = shadowBlur = shadowSpread = 0;

        x = y = w = h = 0;
        layout = null;
        resolvedColor = 0;
    }
}
