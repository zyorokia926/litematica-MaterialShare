package dev.materialshare.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.materialshare.client.config.Configs;
import dev.materialshare.common.Area;
import dev.materialshare.common.ProjectRegistry;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the storage boxes of the selected schematic's project in the world, each in its own colour; the boxes follow
 * the schematic that is selected in Litematica. The two marked corners show as small white boxes.
 */
public final class StorageRender {
    private static final float[][] COLORS = {{1f, 0.55f, 0.1f}, {0.2f, 0.8f, 1f}, {0.85f, 0.3f, 1f}, {0.4f, 1f, 0.3f},
        {1f, 0.9f, 0.2f}, {1f, 0.35f, 0.45f}};

    private StorageRender() {
    }

    /** The projects whose boxes are shown: those of the selected placement's schematic, else the selected project. */
    static List<String> shownProjects() {
        List<String> names = new ArrayList<>();
        try {
            var placement = fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
            java.io.File file = placement == null ? null : placement.getSchematicFile();
            if (file != null) {
                String base = ProjectRegistry.suggestName(file.getName());
                names.addAll(ProjectRegistry.projectsOfList(base,
                        ClientProjectCache.projects().stream().map(p -> p.name()).toList()));
                return names;
            }
        } catch (RuntimeException e) {
            // no usable placement: fall back to the selected project
        }
        ClientProjectCache.selected().ifPresent(p -> names.add(p.name()));
        return names;
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.options.hudHidden) {
            return;
        }
        String dim = client.world.getRegistryKey().getValue().toString();
        List<Area> areas = new ArrayList<>();
        if (Configs.Generic.SHOW_STORAGE.getBooleanValue()) {
            for (String project : shownProjects()) {
                areas.addAll(ClientProjectCache.storage(project).areas());
            }
        }
        boolean marks = StorageMarks.dimension().equals(dim) && (StorageMarks.a() != null || StorageMarks.b() != null);
        List<BlockPos> found = Pickup.shown(dim);
        if (areas.isEmpty() && !marks && found.isEmpty()) {
            return;
        }
        MatrixStack m = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();
        m.push();
        m.translate(-cam.x, -cam.y, -cam.z);
        for (int pass = 0; pass < 2; pass++) {
            float alpha = pass == 0 ? 0.3f : 1f;
            if (pass == 0) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }
            dev.materialshare.McClientCompat.useShaderLines();
            RenderSystem.lineWidth(pass == 0 ? 2f : 3f);
            BufferBuilder buf = Tessellator.getInstance().getBuffer();
            buf.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
            int i = 0;
            for (Area a : areas) {
                if (a.dimension().equals(dim)) {
                    float[] c = COLORS[i % COLORS.length];
                    WorldRenderer.drawBox(m, buf, a.minX(), a.minY(), a.minZ(), a.maxX() + 1, a.maxY() + 1, a.maxZ() + 1,
                            c[0], c[1], c[2], alpha);
                }
                i++;
            }
            if (marks) {
                corner(m, buf, StorageMarks.a(), alpha);
                corner(m, buf, StorageMarks.b(), alpha);
            }
            for (BlockPos p : found) {
                // the chests that hold the material being found: gold, a little larger than the block
                WorldRenderer.drawBox(m, buf, p.getX() - 0.05, p.getY() - 0.05, p.getZ() - 0.05, p.getX() + 1.05,
                        p.getY() + 1.05, p.getZ() + 1.05, 1f, 0.85f, 0.1f, 1f);
            }
            Tessellator.getInstance().draw();
        }
        // a faint fill, so the box reads as a coloured volume and not only as thin lines
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        dev.materialshare.McClientCompat.useShaderPositionColor();
        BufferBuilder fill = Tessellator.getInstance().getBuffer();
        fill.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int k = 0;
        for (Area a : areas) {
            if (a.dimension().equals(dim)) {
                float[] c = COLORS[k % COLORS.length];
                fillBox(m, fill, a.minX(), a.minY(), a.minZ(), a.maxX() + 1, a.maxY() + 1, a.maxZ() + 1, c[0], c[1], c[2], 0.18f);
            }
            k++;
        }
        Tessellator.getInstance().draw();
        if (!found.isEmpty()) {
            // the chests that hold the material being found: a gold fill seen through walls
            RenderSystem.disableDepthTest();
            BufferBuilder gold = Tessellator.getInstance().getBuffer();
            gold.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (BlockPos p : found) {
                fillBox(m, gold, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 1f, 0.85f, 0.1f, 0.4f);
            }
            Tessellator.getInstance().draw();
            RenderSystem.enableDepthTest();
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1f);
        m.pop();
    }

    private static void corner(MatrixStack m, BufferBuilder buf, BlockPos p, float alpha) {
        if (p != null) {
            WorldRenderer.drawBox(m, buf, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 1f, 1f, 1f, alpha);
        }
    }

    private static void fillBox(MatrixStack m, BufferBuilder b, float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float bl, float a) {
        var mat = m.peek().getPositionMatrix();
        float[][] faces = {
            {x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2},
            {x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1},
            {x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1},
            {x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2},
            {x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1},
            {x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2}};
        for (float[] v : faces) {
            for (int i = 0; i < 12; i += 3) {
                b.vertex(mat, v[i], v[i + 1], v[i + 2]).color(r, g, bl, a).next();
            }
        }
    }
}
