package dev.materialshare.common;

/** A box of blocks (both corners included) in one dimension, where a project's storage chests are. */
public record Area(String name, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    /** The longest side a box may have; keeps one scan cheap and the box meaningful. */
    public static final int MAX_SIDE = 256;

    /** The box between two corners, in any order, with a cleaned name. */
    public static Area between(String name, String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Area(Entry.cleanText(name), dimension == null ? "" : dimension, Math.min(x1, x2), Math.min(y1, y2),
                Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** A box that is not too big and has a name and a dimension. */
    public boolean valid() {
        return !name.isBlank() && !dimension.isBlank() && dimension.length() <= 128
                && sizeX() <= MAX_SIDE && sizeY() <= MAX_SIDE && sizeZ() <= MAX_SIDE
                && sizeX() > 0 && sizeY() > 0 && sizeZ() > 0;
    }

    public boolean contains(String dim, int x, int y, int z) {
        return dimension.equals(dim) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Whether the box reaches into the chunk (chunk coordinates). */
    public boolean touchesChunk(int chunkX, int chunkZ) {
        return maxX >= chunkX * 16 && minX <= chunkX * 16 + 15 && maxZ >= chunkZ * 16 && minZ <= chunkZ * 16 + 15;
    }
}
