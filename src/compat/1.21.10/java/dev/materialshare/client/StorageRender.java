package dev.materialshare.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.materialshare.MaterialShare;
import dev.materialshare.client.config.Configs;
import dev.materialshare.common.Area;
import dev.materialshare.common.ProjectRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the storage boxes of the selected schematic's project in the world, each in its own colour; the boxes follow
 * the schematic that is selected in Litematica. The two marked corners show as small white boxes.
 *
 * <p>This is the file for Minecraft 1.21.10 (it replaces src/main's StorageRender in this build): the game draws through
 * render pipelines, so the lines and fills go into render layers (one set with the depth test, one without, for the parts
 * seen through walls). It is called at the end of the main world pass (Fabric API's END_MAIN event).
 */
public final class StorageRender {
    private static final float[][] COLORS = {{1f, 0.55f, 0.1f}, {0.2f, 0.8f, 1f}, {0.85f, 0.3f, 1f}, {0.4f, 1f, 0.3f},
        {1f, 0.9f, 0.2f}, {1f, 0.35f, 0.45f}};

    private static final RenderPipeline LINES_THROUGH = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of(MaterialShare.MOD_ID, "pipeline/lines_through"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build());
    private static final RenderPipeline QUADS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(MaterialShare.MOD_ID, "pipeline/quads"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false).withDepthWrite(false).build());
    private static final RenderPipeline QUADS_THROUGH = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(MaterialShare.MOD_ID, "pipeline/quads_through"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false).withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());

    private static final RenderLayer LINES_2 = lines("lines_through", LINES_THROUGH, 2.0);
    private static final RenderLayer LINES_3 = lines("lines", RenderPipelines.LINES, 3.0);
    private static final RenderLayer FILL = RenderLayer.of(MaterialShare.MOD_ID + "_fill", 1536, false, true, QUADS,
            RenderLayer.MultiPhaseParameters.builder().build(false));
    private static final RenderLayer FILL_THROUGH = RenderLayer.of(MaterialShare.MOD_ID + "_fill_through", 1536, false, true, QUADS_THROUGH,
            RenderLayer.MultiPhaseParameters.builder().build(false));
    private static final BufferAllocator BUFFER = new BufferAllocator(1 << 16);

    private StorageRender() {
    }

    private static RenderLayer lines(String name, RenderPipeline pipeline, double width) {
        return RenderLayer.of(MaterialShare.MOD_ID + "_" + name, 1536, pipeline, RenderLayer.MultiPhaseParameters.builder()
                .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(width))).layering(RenderPhase.VIEW_OFFSET_Z_LAYERING).build(false));
    }

    /** Draws the boxes at the end of the main world pass. */
    public static void register() {
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(ctx -> render(ctx.matrices()));
    }

    /** The projects whose boxes are shown: those of the selected placement's schematic, else the selected project. */
    static List<String> shownProjects() {
        List<String> names = new ArrayList<>();
        try {
            var placement = fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
            java.io.File file = placement == null ? null : dev.materialshare.McClientCompat.file(placement.getSchematicFile());
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

    public static void render(MatrixStack stack) {
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
        MatrixStack m = stack != null ? stack : new MatrixStack();
        Vec3d cam = client.gameRenderer.getCamera().getPos();
        m.push();
        m.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumerProvider.Immediate out = VertexConsumerProvider.immediate(BUFFER);
        // first the parts seen through walls (faint), then the real lines on top
        for (int pass = 0; pass < 2; pass++) {
            float alpha = pass == 0 ? 0.3f : 1f;
            VertexConsumer buf = out.getBuffer(pass == 0 ? LINES_2 : LINES_3);
            int i = 0;
            for (Area a : areas) {
                if (a.dimension().equals(dim)) {
                    float[] c = COLORS[i % COLORS.length];
                    VertexRendering.drawBox(m.peek(), buf, a.minX(), a.minY(), a.minZ(), a.maxX() + 1, a.maxY() + 1, a.maxZ() + 1,
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
                VertexRendering.drawBox(m.peek(), buf, p.getX() - 0.05, p.getY() - 0.05, p.getZ() - 0.05, p.getX() + 1.05,
                        p.getY() + 1.05, p.getZ() + 1.05, 1f, 0.85f, 0.1f, 1f);
            }
            out.draw();
        }
        // a faint fill, so the box reads as a coloured volume and not only as thin lines
        VertexConsumer fill = out.getBuffer(FILL);
        int k = 0;
        for (Area a : areas) {
            if (a.dimension().equals(dim)) {
                float[] c = COLORS[k % COLORS.length];
                fillBox(m, fill, a.minX(), a.minY(), a.minZ(), a.maxX() + 1, a.maxY() + 1, a.maxZ() + 1, c[0], c[1], c[2], 0.18f);
            }
            k++;
        }
        out.draw();
        if (!found.isEmpty()) {
            // the chests that hold the material being found: a gold fill seen through walls
            VertexConsumer gold = out.getBuffer(FILL_THROUGH);
            for (BlockPos p : found) {
                fillBox(m, gold, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 1f, 0.85f, 0.1f, 0.4f);
            }
            out.draw();
        }
        m.pop();
    }

    private static void corner(MatrixStack m, VertexConsumer buf, BlockPos p, float alpha) {
        if (p != null) {
            VertexRendering.drawBox(m.peek(), buf, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 1f, 1f, 1f, alpha);
        }
    }

    private static void fillBox(MatrixStack m, VertexConsumer b, float x1, float y1, float z1, float x2, float y2, float z2,
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
                b.vertex(mat, v[i], v[i + 1], v[i + 2]).color(r, g, bl, a);
            }
        }
    }
}
