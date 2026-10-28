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
    public static final byte BOX = 0, TEXT = 1, SPACER = 2, DIVIDER = 3, SLIDER = 4, INPUT = 5;
    public static final int NONE = -1;

    // Flags: what input the element takes, set by the queries and controls that build it.
    public static final int CLICKABLE = 1, FOCUSABLE = 2, HOVER = 4, DRAG = 8, TOGGLE = 16, KEYS = 32, SCROLL = 64, LIST = 128,
            TEXT_INPUT = 256;
    /** Which ways a scroll container (SCROLL) scrolls: across, down, or both. */
    public static final int SCROLL_X = 512, SCROLL_Y = 1024;
    public static final int CONTEXT_MENU = 2048;
    public java.util.function.Consumer<jui.ui.Menu> contextMenu;
    public jui.ui.Painter.Draw draw;

    // Structure.
    public long id;
    /** Unique to this node's current use: an Element made for an earlier use does not match it. */
    public long gen;
    public int parent, firstChild, lastChild, nextSibling, childCount;
    public byte kind;
    public String label;
    /** The element's state, if it has one: looked up as the node opens, made when a query needs it. */
    public ElementState state;
    public int flags;
    /** Disabled, by itself or an ancestor: takes no input and paints at half opacity. */
    public boolean disabled;
    /** The element's own opacity, 0 to 1; paint multiplies the ancestors'. */
    public float opacity;
    /** No focus ring when this element has focus: the app's focusRing(false). */
    public boolean noFocusRing;
    /**
     * The control shows its focus itself, on a part (see ringOwner) or not
     * at all (a text input's border): no ring around the whole element.
     */
    public boolean ownRing;
    /**
     * For the part of a control that shows the control's focus: the
     * control's node. The ring goes around this part while the control has
     * focus from the keyboard, unless the app turned it off (focusRing(false)).
     */
    public int ringOwner;
    /** Backgrounds while hovered and while pressed (0 for none), and whether it is, as of the commit. */
    public int bgHover, bgPressed;
    boolean hot, down;
    /** A slider's value as a share of its range, and whether it is held. */
    public float fraction;
    public boolean held;
    /**
     * The ui package's Element for this node, reused outside development
     * mode; it goes with the node when the pool lets the node's chunk go.
     * A use's reset keeps it.
     */
    public Object handle;

    // Layout input. NaN is auto (or no bound); Ui.AUTO is an automatic margin.
    public boolean row, reverse;
    public float width, height, minWidth, maxWidth, minHeight, maxHeight;
    public float widthPercent, heightPercent;
    public float grow, shrink, basis;
    /** Gaps between children across and down: along a row gapX, between its lines gapY; a column the other way. */
    public float gapX, gapY;
    /** Placed in the parent's padding box by its insets (NaN unset), out of the flow; and not shown. */
    public boolean absolute, invisible;
    /** Clips what it holds to its padding box. */
    public boolean clip;
    /** A scroll container's offset (in from its state, out clamped), the offset asked for (NaN none), and its content's height. */
    public float scrollY, scrollRequest, contentH;
    /** The same across, for one that scrolls across: 0 for one that does not. */
    public float scrollX, scrollRequestX, contentW;
    /** A list's model, its anchor row among the rows built, and where that row's top goes from its content box's top. */
    public ListModel listModel;
    public int listAnchor;
    public float listAnchorY;
    /** Where among a list's rows the one built out of view for the focus is (-1 for none), and where its top goes. */
    public int listExtra;
    public float listExtraY;
    /** Whether its scroll bar shows (hovered or dragged), and whether its thumb is being dragged. */
    /** Whether its scroll bars show, and whether the vertical or the horizontal thumb is dragged. */
    boolean bars, barDrag, barDragX;
    public float top, right, bottom, left;
    /** Wraps children onto lines, placed by alignContent (START unless set, as MyGO). */
    public boolean wrap;
    public Align alignContent;
    /** Spreads a wrapping container's lines as justify spreads children: SPACE_* (null: by alignContent). */
    public jui.ui.Justify alignContentSpread;
    /** Mirrors a wrapping container's cross axis: the first line at the cross end. */
    public boolean wrapReverse;
    /** Paints a check box's mark over the background, as MyGO's checkPath. */
    public boolean checkMark;
    public float padTop, padRight, padBottom, padLeft;
    public float marTop, marRight, marBottom, marLeft;
    /** The border's width, which also insets the content. */
    public float border;
    public Justify justify;
    public Align alignItems;
    /** Null follows the parent's alignItems. */
    public Align alignSelf;

    // Text input: NaN and 0 (size, weight) and hasTextColor false inherit.
    public String text;
    public int textColor;
    /** Whether textColor was set: any color, transparent too, can be. */
    public boolean hasTextColor;
    public float fontSize;
    public int fontWeight;
    public boolean singleLine;
    /** A text input's placeholder, shown while it is empty; its shown text is in text. */
    public String placeholder;
    /** The pointer's shape over the element: 0 unset, else a Cursor's ordinal plus 1. */
    public byte cursor;
    /** A text input's paint, from the commit: its text's origin x, its line's top and height. */
    public float inputX, inputTop, inputLineH;
    /**
     * And from the build, in its shown text: the caret, the selection (equal
     * ends for none), the composition (-1 for none); whether it has focus,
     * and whether the blinking caret shows.
     */
    public int inputCaret, inputSelA, inputSelB, inputMarkA, inputMarkB;
    public boolean inputFocused, caretShown;
    /** A text input's options in this frame (Element.readOnly). */
    public boolean readOnly, password, noInputMethod;
    public boolean multiline, inputStyled;
    public int areaMinLines, areaMaxLines;

    // Visual, colors as 0xRRGGBBAA.
    public int background, borderColor, shadowColor;
    public float radiusTL, radiusTR, radiusBR, radiusBL;
    public float shadowX, shadowY, shadowBlur, shadowSpread;

    // Layout output: DIPs from the window's top left.
    public float x, y, w, h;
    public TextLayout layout;
    /** The text style after inheritance. */
    public int resolvedColor;
    float resolvedSize;
    int resolvedWeight;

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
        state = null;
        flags = 0;
        contextMenu = null;
        draw = null;
        disabled = false;
        opacity = 1;
        noFocusRing = false;
        ownRing = false;
        ringOwner = NONE;
        bgHover = bgPressed = 0;
        hot = down = false;
        fraction = 0;
        held = false;

        row = reverse = false;
        width = height = minWidth = maxWidth = minHeight = maxHeight = Float.NaN;
        widthPercent = heightPercent = Float.NaN;
        grow = 0;
        shrink = 1;
        basis = Float.NaN;
        gapX = gapY = 0;
        wrap = wrapReverse = checkMark = false;
        alignContent = Align.START;
        alignContentSpread = null;
        absolute = invisible = clip = false;
        scrollY = contentH = 0;
        scrollRequest = Float.NaN;
        scrollX = contentW = 0;
        scrollRequestX = Float.NaN;
        bars = barDrag = barDragX = false;
        listModel = null;
        listAnchor = 0;
        listAnchorY = 0;
        listExtra = -1;
        listExtraY = 0;
        top = right = bottom = left = Float.NaN;
        padTop = padRight = padBottom = padLeft = 0;
        marTop = marRight = marBottom = marLeft = 0;
        border = 0;
        justify = Justify.START;
        alignItems = Align.STRETCH;
        alignSelf = null;

        text = null;
        textColor = 0;
        hasTextColor = false;
        fontSize = Float.NaN;
        fontWeight = 0;
        singleLine = false;
        placeholder = null;
        cursor = 0;
        inputX = inputTop = inputLineH = 0;
        inputCaret = inputSelA = inputSelB = 0;
        inputMarkA = inputMarkB = -1;
        inputFocused = caretShown = readOnly = password = noInputMethod = false;
        multiline = inputStyled = false;
        areaMinLines = areaMaxLines = 0;

        background = borderColor = shadowColor = 0;
        radiusTL = radiusTR = radiusBR = radiusBL = 0;
        shadowX = shadowY = shadowBlur = shadowSpread = 0;

        x = y = w = h = 0;
        layout = null;
        resolvedColor = 0;
        resolvedSize = 0;
        resolvedWeight = 0;
    }
}
