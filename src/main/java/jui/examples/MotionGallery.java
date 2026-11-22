package jui.examples;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.kit.*;
import jui.render.Images;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.DoubleRef;
import jui.ui.Element;
import jui.ui.IntRef;
import jui.ui.Ref;
import jui.ui.TextRef;
import jui.ui.View;

import static jui.ui.Ui.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The jui-kit gallery: the uiarc replicas this kit now carries — buttons that
 * spring, icons that morph, sliders that rubber-band, charts, text effects,
 * the image pipeline, and the overlays — live in one scroll. Run:
 * ./build.sh demo Motion
 */
public final class MotionGallery implements View {
    // Buttons & actions.
    private final BoolRef loading = new BoolRef(false);
    private final TextRef swapWord = new TextRef("");
    // Groups.
    private final IntRef groupSel = new IntRef(1);
    private final IntRef togglesSel = new IntRef(0);
    // Icon morph states.
    private final BoolRef mMenu = new BoolRef(false), mPlay = new BoolRef(false), mPlus = new BoolRef(false),
            mCopy = new BoolRef(false), mTheme = new BoolRef(false), mChev = new BoolRef(false),
            mBell = new BoolRef(false), mHeart = new BoolRef(false);
    // Inputs.
    private final TextRef search = new TextRef("");
    private final TextRef password = new TextRef("Sup3rSecret!");
    private final DoubleRef number = new DoubleRef(42);
    private final TextRef numberText = new TextRef("42");
    private final TextRef tagDraft = new TextRef("");
    private final TextRef shortcut = new TextRef("⌘K");
    private final TextRef inlineName = new TextRef("Aurora Chen");
    private final IntRef morphSel = new IntRef(0);
    private final BoolRef morphOpen = new BoolRef(false);
    // Toggles.
    private final BoolRef chk1 = new BoolRef(true), chk2 = new BoolRef(false), chk3 = new BoolRef(false);
    private final BoolRef wifi = new BoolRef(true), autosave = new BoolRef(false);
    private final IntRef segment = new IntRef(0);
    private final BoolRef yearly = new BoolRef(true);
    private final IntRef stars = new IntRef(4);
    private final IntRef plan = new IntRef(0);
    // Sliders.
    private final DoubleRef volume = new DoubleRef(35);
    private final DoubleRef zoom = new DoubleRef(50);
    private final DoubleRef rulerVal = new DoubleRef(240);
    private final DoubleRef priceLo = new DoubleRef(20), priceHi = new DoubleRef(70);
    // Charts.
    private final float[] lineA = {3, 7, 4, 9, 6, 11, 8};
    private final float[] lineB = {5, 4, 8, 6, 9, 7, 10};
    private final float[] bars = {4, 9, 6, 12, 7, 10, 5};
    private final float[] donut = {42, 28, 18, 12};
    private final float[] spark = {0.2f, 0.5f, 0.35f, 0.7f, 0.55f, 0.9f, 0.75f, 1};
    private final float[] heat = new float[364];
    private final float[] brushData = new float[60];
    private final DoubleRef counter = new DoubleRef(1284);
    private final Ref<Integer> treeDrill = new Ref<>(-1);
    private final Ref<int[]> brushWin = new Ref<>(new int[]{8, 40});
    // Text effects.
    private final IntRef wordIdx = new IntRef(0);
    private final Ref<Long> wordAt = new Ref<>(0L);
    private static final String[] WORDS = {"Saving", "Saved", "Publishing", "Done"};
    // Data.
    private final DoubleRef progress = new DoubleRef(0.45);
    // Overlays.
    private final BoolRef dialogOpen = new BoolRef(false);
    private final BoolRef drawerOpen = new BoolRef(false);
    private final BoolRef ddOpen = new BoolRef(false);
    private final IntRef toastCount = new IntRef(0);
    // Messages.
    private final TextRef chatDraft = new TextRef("");
    private final java.util.List<CommentThread.Comment> comments = new ArrayList<>();
    // Disclosure.
    private final IntRef accordionOpen = new IntRef(0);
    private final IntRef page = new IntRef(1);
    // The generated picture's atlas id (-2 unloaded, -1 unavailable).
    private int imageId = -2;

    public MotionGallery() {
        for (int i = 0; i < heat.length; i++) {
            heat[i] = ((i * 37 + i / 7 * 11) % 13) / 13f;
        }
        for (int i = 0; i < brushData.length; i++) {
            brushData[i] = 50 + (float) (Math.sin(i * 0.3) * 30 + Math.sin(i * 0.11) * 18);
        }
        CommentThread.Comment c1 = new CommentThread.Comment("Maya Lin", "2h", "The press spring on the button feels exactly right now.", 4, false);
        c1.replies.add(new CommentThread.Comment("You", "1h", "Right? The scale transform made it land.", 0, true));
        comments.add(c1);
        comments.add(new CommentThread.Comment("Ravi Patel", "3h", "Charts follow the pointer too — try the crosshair.", 2, false));
    }

    /** One gallery section: a heading and the samples under it. */
    private record Section(String title, java.util.function.Consumer<Context> body) {}

    @Override
    public void build(Context c) {
        column(c).fill().background(c.theme().background()).children(() -> {
            scroll(c).fillWidth().grow(1).children(() -> {
                column(c).width(700).gap(18).padding(18).children(() -> {
                    row(c).gap(10).children(() -> {
                        text(c, "jui-kit").fontSize(22).fontWeight(700).textColor(c.theme().text());
                        text(c, "the uiarc replicas, live").fontSize(13).textColor(c.theme().textMuted());
                        spacer(c).grow(1);
                        ThemeSwitcher.themeSwitcher(c, ThemeSwitcher.Style.PLAIN, false);
                        ThemeSwitcher.themeSwitcher(c, ThemeSwitcher.Style.ECLIPSE, false);
                    });
                    for (Section s : sections()) {
                        Card.card(c, s.title(), null, () -> s.body().accept(c), null);
                    }
                });
            });
        });
    }

    private List<Section> sections() {
        return List.of(
                new Section("Buttons & actions", this::buttons),
                new Section("Button groups", this::groups),
                new Section("Icon morph", this::iconMorphs),
                new Section("Fields", this::fields),
                new Section("Toggles", this::toggles),
                new Section("Sliders", this::sliders),
                new Section("Charts", this::charts),
                new Section("Text effects", this::textEffects),
                new Section("Data & media", this::dataMedia),
                new Section("Overlays", this::overlays),
                new Section("Messages & threads", this::messages),
                new Section("Disclosure & gestures", this::disclosure));
    }

    private void buttons(Context c) {
        column(c).gap(10).children(() -> {
            row(c).gap(8).children(() -> {
                Button.builder(c, "Primary", Button.Variant.PRIMARY, Button.Size.MD);
                Button.builder(c, "Secondary", Button.Variant.SECONDARY, Button.Size.MD);
                Button.builder(c, "Ghost", Button.Variant.GHOST, Button.Size.MD);
                Button.builder(c, "Delete", Button.Variant.DANGER, Button.Size.MD);
                Button.builder(c, "Loading…", Button.Variant.PRIMARY, Button.Size.MD, null, true, false, false);
            });
            row(c).gap(8).alignItems(jui.ui.Align.CENTER).children(() -> {
                CopyButton.copyButton(c, "npx shadcn@latest add @uiarc/button");
                ActionButton.actionButton(c, "Publish", "Publishing", "Published", "Couldn't publish",
                        2200, false, settle -> c.worker().execute(() -> {
                            sleep(900);
                            settle.accept(true);
                        }));
                ConfirmMorph.confirmMorph(c, "Delete project", settle -> settle.accept(true));
                ActionSwap.swap(c, new ActionSwap.State[]{
                        new ActionSwap.State("follow", "Follow"),
                        new ActionSwap.State("following", "Following")}, "Try again", Button.Size.MD,
                        next -> { });
            });
            row(c).gap(8).children(() -> {
                HoldToConfirm.holdToConfirm(c, "Hold to delete", 900, false, () -> { });
                Button.builder(c, "Save", Button.Variant.PRIMARY, Button.Size.MD, "check", false, false, false);
                Button.builder(c, "Menu", Button.Variant.SECONDARY, Button.Size.MD, null, false, false, true);
            });
        });
    }

    private void groups(Context c) {
        column(c).gap(12).children(() -> {
            ButtonGroup.group(c, "Zoom", new ButtonGroup.Segment[]{
                    ButtonGroup.Segment.icon("Zoom out", "minus", () -> { }),
                    ButtonGroup.Segment.value("Level", "100%", () -> { }),
                    ButtonGroup.Segment.icon("Zoom in", "plus", () -> { }),
                    ButtonGroup.Segment.of("Share", "share", () -> { })
            }, new ButtonGroup.MenuItem[]{
                    new ButtonGroup.MenuItem("Duplicate", "doc", false, false, () -> { }),
                    new ButtonGroup.MenuItem("Delete", "trash", true, false, () -> { })
            }, false, false, false, false);
            FloatingButtonGroup.tray(c, "Board actions", new FloatingButtonGroup.Item[]{
                    FloatingButtonGroup.Item.iconOnly("Undo", "arrow-left", () -> { }),
                    FloatingButtonGroup.Item.iconOnly("Redo", "arrow-right", () -> { }),
                    FloatingButtonGroup.Item.separator(),
                    FloatingButtonGroup.Item.of("Comment", "doc", () -> { }),
                    FloatingButtonGroup.Item.of("Present", "play", () -> { })
            }, true, false, false, false, false);
            ExpandingButtonGroup.group(c, "Message actions", new ExpandingButtonGroup.Item[]{
                    ExpandingButtonGroup.Item.of("Reply", "paperplane", () -> { }),
                    ExpandingButtonGroup.Item.done("Archive", "Archived", "download", () -> { }),
                    ExpandingButtonGroup.Item.done("Copy link", "Copied", "doc", () -> { })
            }, "Reply", false, 1400, false);
        });
    }

    private void iconMorphs(Context c) {
        row(c).gap(14).wrap().children(() -> {
            morph(c, "menu↔close", IconMorph.MENU, mMenu);
            morph(c, "play↔pause", IconMorph.PLAY, mPlay);
            morph(c, "plus↔check", IconMorph.PLUS, mPlus);
            morph(c, "copy↔check", IconMorph.COPY, mCopy);
            morph(c, "sun↔moon", IconMorph.THEME, mTheme);
            morph(c, "chevron", IconMorph.CHEVRON, mChev);
            morph(c, "bell", IconMorph.BELL, mBell);
            morph(c, "heart", IconMorph.HEART, mHeart);
        });
    }

    private void morph(Context c, String caption, String name, BoolRef on) {
        column(c).gap(5).alignItems(jui.ui.Align.CENTER).shrink(0).children(() -> {
            Element b = buttonBase(c).size(44, 44).radius(10).label("kit-demo-morph");
            if (b.hovered()) {
                b.background(c.theme().surfaceHover());
            }
            b.children(() -> IconMorph.icon(c, name, on.get(), c.theme().text(), 22));
            if (b.clicked()) {
                on.set(!on.get());
                c.invalidate();
            }
            text(c, caption).singleLine().fontSize(9.5f).textColor(c.theme().textMuted());
        });
    }

    private void fields(Context c) {
        column(c).gap(10).children(() -> {
            row(c).gap(8).children(() -> {
                box(c).grow(1).children(() -> SearchField.searchField(c, search, "Search the gallery…"));
                box(c).grow(1).children(() -> InlineEdit.inlineEdit(c, inlineName));
            });
            PasswordField.passwordField(c, "Password", password, "Type one to watch the meter", true);
            row(c).gap(8).children(() -> {
                box(c).grow(1).children(() -> NumberField.numberField(c, "Opacity %", number, numberText,
                        0, 100, 1));
                box(c).grow(1).children(() -> ShortcutRecorder.recorder(c, "Quick action", shortcut));
            });
            TagInput.tagInput(c, "Tags", "Type and press Enter", tagDraft,
                    (tags, t) -> { });
            row(c).gap(8).children(() -> {
                box(c).grow(1).children(() -> MorphSelect.morphSelect(c, morphSel, morphOpen,
                        new String[]{"Weekly", "Monthly", "Quarterly"}, "Select", false, 150));
                ChipGroup.chipGroup(c, new ChipGroup.Chip[]{
                        new ChipGroup.Chip("Design", 12), new ChipGroup.Chip("Docs", 34),
                        new ChipGroup.Chip("Bugs", 7)});
            });
        });
    }

    private void toggles(Context c) {
        column(c).gap(10).children(() -> {
            row(c).gap(18).children(() -> {
                Checkbox.checkbox(c, "Email alerts", null, chk1, false, false);
                Checkbox.checkbox(c, "Inherit", null, chk2, true, false);
                Checkbox.checkbox(c, "Locked", null, chk3, false, true);
                Switch.toggle(c, "Wi-Fi", "Always on", wifi, false);
                Switch.toggle(c, "Autosave", null, autosave, false);
            });
            row(c).gap(14).alignItems(jui.ui.Align.CENTER).children(() -> {
                SegmentedControl.segmented(c, segment, new String[]{"Day", "Week", "Month"}, null, false, false);
                BillingToggle.billingToggle(c, yearly, "-20%", false);
                Rating.rating(c, stars, 5, 20, false,
                        new String[]{"Poor", "Fair", "Good", "Great", "Love it"});
            });
            RadioCards.radioCards(c, plan, new RadioCards.Card[]{
                    new RadioCards.Card("Starter", "For one workspace", "$0", "bolt"),
                    new RadioCards.Card("Pro", "Everything, unlimited", "$12/mo", "star"),
                    new RadioCards.Card("Team", "Pro plus SSO and audit", "$24/mo", "person")
            }, 190, false);
            ToggleGroup.toggles(c, togglesSel, new ToggleGroup.Toggle[]{
                    new ToggleGroup.Toggle("Bold", null, false),
                    new ToggleGroup.Toggle("Italic", null, false),
                    new ToggleGroup.Toggle("Underline", null, true),
                    new ToggleGroup.Toggle("Code", null, false)
            }, false, false, false);
        });
    }

    private void sliders(Context c) {
        column(c).gap(14).children(() -> {
            Sliders.slider(c, volume, 0, 100, 1, false, v -> String.format("%.0f%%", v));
            Sliders.elastic(c, zoom, 0, 100, 5, new double[]{25, 50, 75}, false);
            Sliders.ruler(c, rulerVal, 0, 500, 10, false);
            Sliders.rangeHistogram(c, priceLo, priceHi, 0, 100, bars, false);
            row(c).gap(10).children(() -> {
                text(c, String.format("vol %.0f  zoom %.0f  ruler %.0f", volume.get(), zoom.get(),
                        rulerVal.get())).fontSize(10.5f).textColor(c.theme().textMuted());
            });
        });
    }

    private void charts(Context c) {
        column(c).gap(14).children(() -> {
            Charts.lineChart(c, new String[]{"Signups", "Churn"}, new float[][]{lineA, lineB}, 14,
                    new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}, 160);
            row(c).gap(16).alignItems(jui.ui.Align.CENTER).children(() -> {
                Charts.donutChart(c, new String[]{"Direct", "Search", "Social", "Referral"}, donut, 130, 0);
                column(c).gap(10).children(() -> {
                    Charts.barChart(c, new String[]{"M", "T", "W", "T", "F", "S", "S"}, bars, 14, 110);
                    row(c).gap(10).children(() -> {
                        Charts.gauge(c, 68, 0, 100, "%", 90);
                        column(c).gap(4).children(() -> {
                            text(c, "Trend").fontSize(10).textColor(c.theme().textMuted());
                            Charts.sparkline(c, spark, 110, 34, null);
                            Charts.animatedCounter(c, counter, "", " sessions", 16);
                        });
                    });
                });
            });
            Charts.brushChart(c, brushData, brushWin, 110, 180);
            Charts.activityHeatmap(c, heat, 1, 640);
            Charts.treemap(c, new String[]{"Docs", "Deps", "Tests", "Src"},
                    new float[]{30, 45, 15, 10}, treeDrill, 640, 150);
        });
    }

    private void textEffects(Context c) {
        column(c).gap(10).children(() -> {
            TextEffects.textReveal(c, "Words rise in one by one, on the morph run.", 15, true);
            // A label cycling through its words, letter by letter.
            if (c.now() - wordAt.get() > 1_600_000_000L) {
                wordAt.set(c.now());
                wordIdx.set((wordIdx.get() + 1) % WORDS.length);
            }
            c.after(java.time.Duration.ofMillis(64));
            row(c).gap(8).alignItems(jui.ui.Align.CENTER).children(() -> {
                TextEffects.textMorph(c, WORDS[wordIdx.get()], 18);
                TextEffects.textShimmer(c, "Loading your workspace…", 13, true);
            });
            row(c).gap(8).alignItems(jui.ui.Align.CENTER).children(() -> {
                TextEffects.slotText(c, String.format("%04d", 60 + wordIdx.get() * 111), 18, "0123456789");
                text(c, "slots spin to their digits").fontSize(10.5f).textColor(c.theme().textMuted());
            });
        });
    }

    private void dataMedia(Context c) {
        column(c).gap(12).children(() -> {
            row(c).gap(10).alignItems(jui.ui.Align.STRETCH).children(() -> {
                box(c).grow(1).children(() -> MetricCard.metricCard(c, "Weekly active", "12,847",
                        12.4, spark));
                box(c).grow(1).children(() -> UsageMeter.usageMeter(c, "AI credits", 46, 50, "cr"));
            });
            row(c).gap(8).alignItems(jui.ui.Align.CENTER).children(() -> {
                Badge.badge(c, "New", Kit.Variant.INFO, true);
                Badge.badge(c, "Shipped", Kit.Variant.SUCCESS, false);
                Badge.badge(c, "Deprecated", Kit.Variant.DANGER, true);
                Badge.badge(c, 127);
                Badge.badgeDot(c);
                Progress.bar(c, progress, 120);
                Progress.ring(c, progress, 26);
            });
            row(c).gap(12).alignItems(jui.ui.Align.CENTER).children(() -> {
                // A picture the image pipeline draws: generated pixels, the
                // same road a decoded PNG takes through the atlas.
                if (imageId == -2) {
                    imageId = makePicture();
                }
                if (imageId >= 0) {
                    Image.image(c, imageId, 132, 96, 12);
                } else {
                    Image.placeholder(c, 132, 96, 12, "atlas idle");
                }
                AvatarGroup.avatarGroup(c, new String[]{"Li Ming", "Ana Cruz", "Ravi Patel",
                        "Maya Lin", "Sam Okafor", "Theo Brand"});
                Frost.panel(c, 0.5f, Kit.RADIUS + 4, () -> {
                    column(c).padding(10).gap(3).children(() -> {
                        text(c, "Frost panel").fontSize(11.5f).fontWeight(500).textColor(c.theme().text());
                        text(c, "the blur stand-in, over content").fontSize(10)
                                .textColor(c.theme().textMuted());
                    });
                });
            });
            EmptyState.emptyState(c, "folder", "No deployments yet", "Connect a repository to see builds land here.",
                    "New deployment", () -> dialogOpen.set(true));
        });
    }

    /** A little generated landscape: sky-to-grass gradient with a sun, into the image atlas. */
    private int makePicture() {
        try {
            if (!Images.available()) {
                return -1;
            }
            int w = 132, h = 96;
            int[] px = new int[w * h];
            int skyTop = 0xFF2E5AA8, skyBot = 0xFF9CC3F0, grass = 0xFF2E7D43, sun = 0xFFF5D442;
            for (int y = 0; y < h; y++) {
                float t = Math.min(1, y / 58f);
                int r = (int) (((skyTop >>> 16) & 0xFF) + (((skyBot >>> 16) & 0xFF) - ((skyTop >>> 16) & 0xFF)) * t);
                int g = (int) (((skyTop >>> 8) & 0xFF) + (((skyBot >>> 8) & 0xFF) - ((skyTop >>> 8) & 0xFF)) * t);
                int b = (int) ((skyTop & 0xFF) + ((skyBot & 0xFF) - (skyTop & 0xFF)) * t);
                for (int x = 0; x < w; x++) {
                    int col;
                    if (y >= 58) {
                        float shade = 1 - (y - 58) / 60f * 0.35f;
                        col = 0xFF000000 | ((int) (((grass >>> 16) & 0xFF) * shade) << 16)
                                | ((int) (((grass >>> 8) & 0xFF) * shade) << 8)
                                | (int) ((grass & 0xFF) * shade);
                    } else {
                        col = 0xFF000000 | r << 16 | g << 8 | b;
                    }
                    float dx = x - 26, dy = y - 18;
                    if (dx * dx + dy * dy < 14 * 14) {
                        col = sun;
                    }
                    px[y * w + x] = col;
                }
            }
            return Images.add(w, h, px);
        } catch (Throwable t) {
            return -1;
        }
    }

    private void overlays(Context c) {
        column(c).gap(10).children(() -> {
            row(c).gap(8).alignItems(jui.ui.Align.CENTER).children(() -> {
                Element b1 = Button.builder(c, "Dialog", Button.Variant.SECONDARY, Button.Size.SM, "doc",
                        false, false, false);
                if (b1.clicked()) {
                    dialogOpen.set(true);
                }
                Element b2 = Button.builder(c, "Drawer", Button.Variant.SECONDARY, Button.Size.SM, "list-bullet",
                        false, false, false);
                if (b2.clicked()) {
                    drawerOpen.set(true);
                }
                DropdownMenu.dropdown(c, "Actions", "ellipsis", new DropdownMenu.Item[]{
                        new DropdownMenu.Item("Rename", "pencil", () -> { }),
                        new DropdownMenu.Item("Duplicate", "doc", "⌘D", false, false, () -> { }),
                        new DropdownMenu.Item("Delete", "trash", null, true, false, () -> { })
                });
                Element t = Button.builder(c, "Toast", Button.Variant.SECONDARY, Button.Size.SM, "paperplane",
                        false, false, false);
                if (t.clicked()) {
                    toastCount.set(toastCount.get() + 1);
                    c.invalidate();
                }
                HoverCard.hoverCard(c, hoverAnchor(c), new BoolRef(false),
                        () -> HoverCard.personCard(c, "Maya Lin", "@mayal", "Design systems. Springs over snaps."));
            });
            Dialog.dialog(c, dialogOpen, "Delete deployment?", "trash", true,
                    () -> text(c, "This removes the preview URL and its logs. There is no undo.")
                            .fontSize(12).textColor(c.theme().textMuted()),
                    Dialog.Buttons.OK_CANCEL, ok -> { });
            Drawer.drawer(c, drawerOpen, Drawer.Side.RIGHT, 300, "Details",
                    () -> Skeleton.textLines(c, 260));
            if (toastCount.get() > 0) {
                // A fresh identity per trigger, so the toast's clock starts now.
                c.key(toastCount.get());
                Toast.toast(c, Toast.Kind.SUCCESS, "Deployed", "prod is live at v1.42.0", "View", () -> {
                }, new BoolRef(true));
            }
        });
    }

    private Element hoverAnchor(Context c) {
        Element anchor = buttonBase(c).height(28).padding(0, 10).radius(Kit.RADIUS_XS + 2)
                .label("kit-demo-hover");
        anchor.children(() -> {
            Avatar.avatar(c, "Maya Lin", 20);
            text(c, "Maya Lin").singleLine().fontSize(12).textColor(c.theme().text());
        });
        return anchor;
    }

    private void messages(Context c) {
        column(c).gap(12).children(() -> {
            AnnouncementBar.announcementBar(c, new String[]{"v1.42 shipped: springs everywhere",
                    "New: the image pipeline", "Charts now follow the pointer"}, null, "What's new",
                    () -> dialogOpen.set(true));
            Timeline.timeline(c, new Timeline.Event[]{
                    new Timeline.Event("Today", "14:02", "Maya Lin", "merged the transform branch"),
                    new Timeline.Event("Today", "09:41", "Ravi Patel", "cut release v1.42.0"),
                    new Timeline.Event("Yesterday", "17:20", "You", "opened the image atlas PR")
            });
            CommentThread.comments(c, comments);
            ChatThread.chat(c, new ChatThread.Message[]{
                    new ChatThread.Message("Maya Lin", "14:00", "Try the elastic slider past its end — it rubber-bands.", false),
                    new ChatThread.Message("You", "14:01", "And the ruler snaps to ticks. Feels great.", true)
            }, true, chatDraft, sent -> { });
            CodeBlock.codeBlock(c, "bash", "./build.sh demo Motion\n# the gallery you are looking at");
            java.util.LinkedHashMap<String, Object> json = new java.util.LinkedHashMap<>();
            json.put("release", "1.42.0");
            json.put("springs", true);
            json.put("components", java.util.List.of("button", "slider", "chart"));
            JsonViewer.jsonViewer(c, json, "", 120);
        });
    }

    private void disclosure(Context c) {
        column(c).gap(10).children(() -> {
            Breadcrumb.breadcrumb(c, "Workspace", "Settings", "Appearance");
            Accordion.accordion(c, accordionOpen, false, true,
                    Accordion.item("What moved into the engine?", () ->
                            text(c, "Rotate, scale, gradients, runLater, a worker pool, and the image atlas.")
                                    .fontSize(11.5f).textColor(c.theme().textMuted())),
                    Accordion.item("What is still approximate?", () ->
                            text(c, "Backdrop blur: Frost stands in until the offscreen pass lands.")
                                    .fontSize(11.5f).textColor(c.theme().textMuted())));
            row(c).gap(10).children(() -> {
                box(c).grow(1).children(() -> ScrollArea.scrollArea(c, 84, () -> {
                    for (int i = 1; i <= 8; i++) {
                        int n = i;
                        text(c, "Scroll line " + n).fontSize(11).textColor(c.theme().textMuted())
                                .padding(2, 0);
                    }
                }));
                box(c).grow(1).children(() -> ExpandableText.expandableText(c,
                        "Long text clamps to a few lines with a soft fade, then glides open with Read more " +
                                "and closed with Show less. The toggle only shows when the text overflows " +
                                "its clamp, so short passages never carry it."));
            });
            SwipeActions.swipeRow(c, new SwipeActions.Action[]{
                    new SwipeActions.Action("Archive", "download", false, () -> { }),
                    new SwipeActions.Action("Delete", "trash", true, () -> { })
            }, () -> row(c).fillWidth().padding(0, 12).alignItems(jui.ui.Align.CENTER).children(() -> {
                text(c, "A swipeable row — drag it left").fontSize(12).textColor(c.theme().text());
                spacer(c).grow(1);
                text(c, "↔").textColor(c.theme().textMuted());
            }));
            row(c).gap(14).alignItems(jui.ui.Align.CENTER).children(() -> {
                Reactions.reactions(c, new Reactions.Reaction[]{
                        new Reactions.Reaction("🎉", 12, false), new Reactions.Reaction("🚀", 5, true),
                        new Reactions.Reaction("👀", 3, false)});
                Pagination.pagination(c, page, 9);
            });
        });
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        App.menu(bar -> { });
        App.run(() -> Window.open(WindowOptions.of("动效画廊 — jui-kit").size(760, 940)
                .content(new MotionGallery()::build)));
    }
}
