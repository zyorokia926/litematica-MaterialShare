package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import dev.materialshare.common.ProjectRegistry.TrashResult;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

class TrashTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static ProjectRegistry registryWith(String... names) {
        ProjectRegistry r = new ProjectRegistry();
        for (String n : names) {
            r.getOrCreate(n).merge(Map.of("minecraft:stone", 10));
        }
        return r;
    }

    @Test
    void deletingMovesTheProjectToTheTrash() {
        ProjectRegistry r = registryWith("castle");
        assertEquals(TrashResult.OK, r.moveToTrash("castle", A, 1000L));
        assertTrue(r.get("castle").isEmpty());
        assertEquals(1, r.trashed().size());
        ProjectRegistry.Trashed t = r.trashed().get(0);
        assertEquals("castle", t.project().name());
        assertEquals(1000L, t.time());
        assertEquals(A, t.by());
        assertEquals(10, t.project().totalRequired());
    }

    @Test
    void unknownProjectCannotBeTrashed() {
        assertEquals(TrashResult.NOT_FOUND, new ProjectRegistry().moveToTrash("nope", A, 1L));
    }

    @Test
    void restoreBringsTheProjectBackWithItsData() {
        ProjectRegistry r = registryWith("castle");
        r.get("castle").get().setClaim(A, "minecraft:stone", true);
        r.moveToTrash("castle", A, 5L);
        long id = r.trashed().get(0).id();
        assertEquals(TrashResult.OK, r.restore(id));
        assertTrue(r.trashed().isEmpty());
        assertTrue(r.get("castle").get().items().get("minecraft:stone").claims().containsKey(A));
    }

    @Test
    void restoreIsRefusedWhileAnotherProjectHasTheName() {
        ProjectRegistry r = registryWith("castle");
        r.moveToTrash("castle", A, 5L);
        r.getOrCreate("castle");
        long id = r.trashed().get(0).id();
        assertEquals(TrashResult.NAME_TAKEN, r.restore(id));
        assertEquals(1, r.trashed().size());
    }

    @Test
    void sameNameCanBeTrashedTwice() {
        ProjectRegistry r = registryWith("castle");
        r.moveToTrash("castle", A, 5L);
        r.getOrCreate("castle");
        r.moveToTrash("castle", A, 5L); // same millisecond
        assertEquals(2, r.trashed().size());
        assertNotEquals(r.trashed().get(0).id(), r.trashed().get(1).id());
    }

    @Test
    void purgeRemovesForGood() {
        ProjectRegistry r = registryWith("castle");
        r.moveToTrash("castle", A, 5L);
        long id = r.trashed().get(0).id();
        assertTrue(r.purge(id));
        assertTrue(r.trashed().isEmpty());
        assertFalse(r.purge(id));
        assertEquals(TrashResult.NOT_FOUND, r.restore(id));
    }

    @Test
    void trashHasALimitInsteadOfSilentlyForgetting() {
        ProjectRegistry r = new ProjectRegistry();
        for (int i = 0; i < ProjectRegistry.MAX_TRASH; i++) {
            r.getOrCreate("p");
            assertEquals(TrashResult.OK, r.moveToTrash("p", A, i));
        }
        r.getOrCreate("p");
        assertEquals(TrashResult.FULL, r.moveToTrash("p", A, 99999L));
        assertTrue(r.get("p").isPresent(), "the project stays when the trash is full");
    }

    @Test
    void trashedProjectsDoNotCountAgainstTheProjectLimit() {
        ProjectRegistry r = new ProjectRegistry();
        for (int i = 0; i < ProjectRegistry.MAX_PROJECTS; i++) {
            r.getOrCreate("p" + i);
        }
        assertFalse(r.canCreate("new"));
        r.moveToTrash("p0", A, 1L);
        assertTrue(r.canCreate("new"));
    }

    @Test
    void restoreIsRefusedWhenTheProjectLimitIsReached() {
        ProjectRegistry r = new ProjectRegistry();
        r.getOrCreate("old");
        r.moveToTrash("old", A, 1L);
        for (int i = 0; i < ProjectRegistry.MAX_PROJECTS; i++) {
            r.getOrCreate("p" + i);
        }
        assertEquals(TrashResult.TOO_MANY, r.restore(r.trashed().get(0).id()));
    }

    @Test
    void trashSummaryRoundTripsThroughTheWire() {
        ProjectRegistry r = registryWith("castle");
        r.get("castle").get().setClaim(A, "minecraft:stone", true);
        r.get("castle").get().setCollected(A, "minecraft:stone", 4);
        r.moveToTrash("castle", A, 777L);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeTrash(buf, r.trashed());
        List<Packets.TrashEntry> back = Packets.readTrash(buf);
        assertEquals(1, back.size());
        Packets.TrashEntry e = back.get(0);
        assertEquals("castle", e.name());
        assertEquals(777L, e.time());
        assertEquals(A, e.by());
        assertEquals(4, e.collected());
        assertEquals(10, e.required());
        assertEquals(r.trashed().get(0).id(), e.id());
    }

    @Test
    void hostileTrashLengthIsCapped() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(Integer.MAX_VALUE);
        assertTrue(Packets.readTrash(buf).isEmpty());
    }

    @Test
    void archivedFlagSurvivesTheWire() {
        Project p = new Project("castle");
        p.setArchived(true);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeProject(buf, p);
        assertTrue(Packets.readProject(buf).archived());
        Project q = new Project("tower");
        buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeProject(buf, q);
        assertFalse(Packets.readProject(buf).archived());
    }
}
