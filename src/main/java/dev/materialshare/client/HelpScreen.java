package dev.materialshare.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.materialshare.MaterialShare;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * The how-to: every feature of the add-on on its own page, with pictures of the real screens. The pages are listed
 * in {@link #PAGES}; each has a title and a body in the language files ({@code help.<id>.title}, {@code help.<id>.body})
 * and its pictures in {@code textures/gui/help/<ja|en>/<name>.png}. When a feature changes, change its page too
 * (see CLAUDE.md).
 */
public class HelpScreen extends GuiBase implements NoWheelClick {
    /** One page of the guide: its id (for the texts) and the pictures it shows. */
    public record Page(String id, List<String> images) {
    }

    public static final List<Page> PAGES = List.of(
            new Page("start", List.of("list")),
            new Page("share", List.of("project")),
            new Page("claim", List.of("list")),
            new Page("amounts", List.of("list")),
            new Page("filter", List.of("list")),
            new Page("split", List.of("split")),
            new Page("bulk", List.of("bulk")),
            new Page("tools", List.of("tools0", "tools1")),
            new Page("missing", List.of("missing")),
            new Page("totals", List.of("totals")),
            new Page("storage", List.of("storage_chat", "storage", "stock", "storage_world")),
            new Page("stats", List.of("stats", "history")),
            new Page("items", List.of("iteminfo")),
            new Page("surplus", List.of("surplus")),
            new Page("breakdown", List.of("breakdown")),
            new Page("manage", List.of("trash", "tools3")),
            new Page("settings", List.of("config", "hudplace")),
            new Page("commands", List.of()),
            new Page("server", List.of()));

    /**
     * The protocol version the pages were last checked against. Every new feature that touches the packets raises
     * {@link dev.materialshare.common.ProtocolCheck#PROTOCOL_VERSION}; a test fails until the pages were reviewed
     * (and new pages or pictures added) and this number is raised to match.
     */
    static final int REVIEWED_PROTOCOL = 16;

    private static final int NAV_Y = 28;
    private static final int TOP = 54;
    private static final Map<Identifier, int[]> SIZES = new HashMap<>();

    private final int page;
    private int scroll;
    private int contentHeight;

    public HelpScreen(Screen parent, int page) {
        this.page = Math.max(0, Math.min(page, PAGES.size() - 1));
        this.setParent(parent);
        this.setTitle(Lang.t("help.title"));
    }

    /** A text of a page ({@code title}, {@code short} or {@code body}). */
    static String text(Page page, String part) {
        String key = "help." + page.id() + "." + part;
        return Lang.t(key);
    }

    static String language() {
        String lang = MinecraftClient.getInstance().options.language;
        return lang != null && lang.startsWith("ja") ? "ja" : "en";
    }

    static Identifier textureOf(String name) {
        return new Identifier(MaterialShare.MOD_ID, "textures/gui/help/" + language() + "/" + name + ".png");
    }

    /** Width and height of a picture; null when the file is not in the jar. */
    static int[] sizeOf(Identifier id) {
        if (SIZES.containsKey(id)) {
            return SIZES.get(id);
        }
        int[] size = null;
        try {
            try (InputStream in = dev.materialshare.McClientCompat.openResource(id)) {
                if (in != null) {
                    try (NativeImage image = NativeImage.read(in)) {
                        size = new int[] {image.getWidth(), image.getHeight()};
                    }
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            size = null;
        }
        SIZES.put(id, size);
        return size;
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = 12;
        int y = NAV_Y;
        String[] labels = {Lang.t("common.back"), Lang.t("common.prev"), Lang.t("common.next"), Lang.t("help.topics")};
        for (int i = 0; i < labels.length; i++) {
            int w = Lang.buttonWidth(this, labels[i], 40);
            if (x + w > this.width - 12 && x > 12) {
                x = 12;
                y += 24;
            }
            ButtonGeneric button = new ButtonGeneric(x, y, w, 20, labels[i]);
            switch (i) {
                case 0 -> addButton(button, (b, mb) -> openGui(getParent()));
                case 1 -> {
                    button.setEnabled(page > 0);
                    addButton(button, (b, mb) -> openGui(new HelpScreen(getParent(), page - 1)));
                }
                case 2 -> {
                    button.setEnabled(page < PAGES.size() - 1);
                    addButton(button, (b, mb) -> openGui(new HelpScreen(getParent(), page + 1)));
                }
                default -> addButton(button, (b, mb) -> openGui(new HelpTopicsScreen(this, 0, "")));
            }
            x += w + 4;
        }
        String counter = (page + 1) + " / " + PAGES.size();
        this.counterX = x + 4;
        this.counterY = y + 6;
        this.counter = counter;
        this.contentTop = y + 28;
    }

    private String counter = "";
    private int counterX;
    private int counterY;
    private int contentTop = TOP;

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double amount) {
        int visible = this.height - contentTop - 6;
        int max = Math.max(0, contentHeight - visible);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount) * 24));
        return true;
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        MinecraftClient client = MinecraftClient.getInstance();
        client.textRenderer.drawWithShadow(matrices, counter, counterX, counterY, 0xFFAAAAAA);
        Page p = PAGES.get(page);
        int x = 14;
        int width = Math.min(this.width - 28, 620);
        int bottom = this.height - 6;
        int visible = bottom - contentTop;
        double scale = client.getWindow().getScaleFactor();
        RenderSystem.enableScissor(0, (int) (client.getWindow().getFramebufferHeight() - bottom * scale),
                (int) (this.width * scale), (int) (visible * scale));
        int y = contentTop - scroll;
        y = UiText.drawWrapped(matrices, "§e" + text(p, "title"), x, y, width, 0xFFFFFFFF) + 4;
        y = UiText.drawWrapped(matrices, text(p, "body"), x, y, width, 0xFFDDDDDD) + 8;
        y = drawImages(matrices, p, x, y, width);
        RenderSystem.disableScissor();
        contentHeight = y + scroll - contentTop + 4;
        if (contentHeight > visible) {
            client.textRenderer.drawWithShadow(matrices, scroll > 0 ? "▲" : " ", this.width - 14, contentTop, 0xFFAAAAAA);
            client.textRenderer.drawWithShadow(matrices, scroll < contentHeight - visible ? "▼" : " ", this.width - 14, bottom - 9, 0xFFAAAAAA);
        }
    }

    /** The pictures of the page, side by side when two fit, else one below the other, each at most 360 wide. */
    private int drawImages(MatrixStack matrices, Page p, int x, int y, int width) {
        List<Identifier> shown = new java.util.ArrayList<>();
        for (String name : p.images()) {
            Identifier id = textureOf(name);
            if (sizeOf(id) != null) {
                shown.add(id);
            }
        }
        if (shown.isEmpty()) {
            return y;
        }
        boolean sideBySide = shown.size() == 2 && width >= 560;
        int each = sideBySide ? (width - 8) / 2 : Math.min(width, 400);
        int rowBottom = y;
        int cx = x;
        for (Identifier id : shown) {
            int[] size = sizeOf(id);
            int w = Math.min(each, size[0]);
            int h = Math.max(1, size[1] * w / size[0]);
            RenderSystem.setShaderTexture(0, id);
            RenderSystem.enableBlend();
            DrawableHelper.drawTexture(matrices, cx, y, w, h, 0, 0, size[0], size[1], size[0], size[1]);
            DrawableHelper.fill(matrices, cx - 1, y - 1, cx + w + 1, y, 0xFF888888);
            DrawableHelper.fill(matrices, cx - 1, y + h, cx + w + 1, y + h + 1, 0xFF888888);
            DrawableHelper.fill(matrices, cx - 1, y, cx, y + h, 0xFF888888);
            DrawableHelper.fill(matrices, cx + w, y, cx + w + 1, y + h, 0xFF888888);
            if (sideBySide) {
                cx += each + 8;
                rowBottom = Math.max(rowBottom, y + h + 8);
            } else {
                y += h + 10;
                rowBottom = y;
            }
        }
        return rowBottom;
    }
}
