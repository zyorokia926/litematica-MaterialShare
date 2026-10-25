package dev.materialshare.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;

/** Small text helpers for the screens of this mod. */
public final class UiText {
    private static final int LINE_HEIGHT = 10;

    private UiText() {
    }

    /** Height the text takes when wrapped to {@code maxWidth} (so what follows can be placed below it). */
    static int wrappedHeight(String text, int maxWidth) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        return wrap(text, Math.max(40, maxWidth)).size() * LINE_HEIGHT;
    }

    /** Draws text wrapped to {@code maxWidth}; returns the y position below the last line. */
    static int drawWrapped(MatrixStack matrices, String text, int x, int y, int maxWidth, int color) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        for (String line : wrap(text, Math.max(40, maxWidth))) {
            font.drawWithShadow(matrices, line, x, y, color);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private static final String NOT_AT_LINE_START = "。、，．・：；！？」』）】〉》ー）)]!?,.:;%";
    private static final String NOT_AT_LINE_END = "「『（【〈《([";

    private static boolean wide(int c) {
        return (c >= 0x3000 && c <= 0x9FFF) || (c >= 0xFF00 && c <= 0xFFEF) || (c >= 0xAC00 && c <= 0xD7AF);
    }

    /**
     * Wraps text to the width. Spaces and every Japanese/Chinese/Korean character are break points (the game's own
     * wrapping only breaks at spaces, so a Japanese sentence jumped whole to the next line), and a closing mark such as
     * "。" never starts a line nor an opening bracket ends one. A colour code stays on in the lines that follow.
     */
    static java.util.List<String> wrap(String text, int maxWidth) {
        if (text.indexOf('\n') >= 0) {
            java.util.List<String> all = new java.util.ArrayList<>();
            String carry = "";
            for (String part : text.split("\n", -1)) {
                for (String line : wrap(carry + part, maxWidth)) {
                    all.add(line);
                    carry = lineCarry(line);
                }
            }
            return all;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lastBreak = -1; // index in line after which it may be cut
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            int len = Character.charCount(c);
            String ch = text.substring(i, i + len);
            if (c == '\u00a7' && i + 1 < text.length()) {
                ch = text.substring(i, i + 2);
                len = 2;
            }
            if (ch.charAt(0) == '\u00a7' && ch.length() == 2) {
                line.append(ch);
                i += len;
                continue;
            }
            line.append(ch);
            if (font.getWidth(line.toString()) > maxWidth && line.length() > ch.length()) {
                int cut = lastBreak > 0 ? lastBreak : line.length() - ch.length();
                String head = line.substring(0, cut);
                String tail = line.substring(cut);
                // never start a line with a closing mark: take the previous character along
                while (!tail.isEmpty() && NOT_AT_LINE_START.indexOf(tail.codePointAt(0)) >= 0 && head.length() > 1
                        && head.charAt(head.length() - 1) != ' ') {
                    int back = Character.charCount(head.codePointBefore(head.length()));
                    tail = head.substring(head.length() - back) + tail;
                    head = head.substring(0, head.length() - back);
                }
                while (!head.isEmpty() && NOT_AT_LINE_END.indexOf(head.codePointBefore(head.length())) >= 0 && head.length() > 1) {
                    int back = Character.charCount(head.codePointBefore(head.length()));
                    tail = head.substring(head.length() - back) + tail;
                    head = head.substring(0, head.length() - back);
                }
                String shown = head.stripTrailing();
                lines.add(shown);
                String prefix = lineCarry(shown);
                line = new StringBuilder(prefix + tail.stripLeading());
                lastBreak = -1;
            }
            i += len;
            if (c == ' ') {
                lastBreak = line.length();
            } else if (wide(c)) {
                lastBreak = line.length();
            }
        }
        lines.add(line.toString());
        return lines;
    }

    /** The formatting codes in effect at the end of the line, to start the next line with. */
    private static String lineCarry(String line) {
        StringBuilder active = new StringBuilder();
        for (int i = 0; i + 1 < line.length(); i++) {
            if (line.charAt(i) == '\u00a7') {
                char code = Character.toLowerCase(line.charAt(i + 1));
                if (code == 'r') {
                    active.setLength(0);
                } else if ("0123456789abcdef".indexOf(code) >= 0) {
                    active.setLength(0);
                    active.append('\u00a7').append(code);
                } else {
                    active.append('\u00a7').append(code);
                }
                i++;
            }
        }
        return active.toString();
    }

    /** The widest a tooltip may be: well inside the screen, so it is never cut off at the edge. */
    private static int tipWidth() {
        var screen = MinecraftClient.getInstance().currentScreen;
        int screenWidth = screen == null ? 427 : screen.width;
        return Math.max(120, Math.min(280, screenWidth * 2 / 5));
    }

    /** Hover text for a button: wrapped onto more lines when it would not fit, one entry per line. */
    public static String[] tip(String text) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String part : text.split("\n", -1)) {
            lines.addAll(wrap(part, tipWidth()));
        }
        return lines.toArray(new String[0]);
    }

    /** The same for a tooltip drawn by the game: each line is wrapped to the allowed width. */
    static java.util.List<net.minecraft.text.OrderedText> tipLines(java.util.List<net.minecraft.text.Text> lines) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        java.util.List<net.minecraft.text.OrderedText> result = new java.util.ArrayList<>();
        for (net.minecraft.text.Text line : lines) {
            result.addAll(font.wrapLines(line, tipWidth()));
        }
        return result;
    }

    /** The text cut to the width (with an ellipsis), so a long name never runs out of its frame. */
    public static String fit(String text, int maxWidth) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        if (font.getWidth(text) <= maxWidth) {
            return text;
        }
        String cut = font.trimToWidth(text, Math.max(0, maxWidth - font.getWidth("…")));
        return cut + "…";
    }

    /** Texts shown through Minecraft's translation are formatted: a "%" in a player or project name must be doubled. */
    static String escapeFormat(String text) {
        return text.replace("%", "%%");
    }

    /** The display name of an item id, or the id itself if it is not a known item. */
    static String itemName(String id) {
        net.minecraft.util.Identifier identifier = net.minecraft.util.Identifier.tryParse(id);
        if (identifier == null || !dev.materialshare.McCompat.hasItem(identifier)) {
            return id;
        }
        return dev.materialshare.McCompat.item(identifier).getName().getString();
    }
}
