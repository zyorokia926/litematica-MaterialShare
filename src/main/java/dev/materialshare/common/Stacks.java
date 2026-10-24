package dev.materialshare.common;

/** A count written as stacks: 700 of a 64-stack item is "10 stacks + 60". */
public final class Stacks {
    private Stacks() {
    }

    /** How many shulker boxes (27 stacks) the count fills, when it is at least half of one; otherwise 0. */
    public static double shulkers(int count, int stackSize) {
        if (stackSize < 1) {
            return 0;
        }
        double boxes = count / (27.0 * stackSize);
        return boxes >= 0.5 ? Math.round(boxes * 10) / 10.0 : 0;
    }

    /** Empty when the count is smaller than one stack, or the item does not stack (so nothing is added to the text). */
    public static String format(int count, int stackSize, Translator tr) {
        if (stackSize <= 1 || count < stackSize) {
            return "";
        }
        int stacks = count / stackSize;
        int rest = count % stackSize;
        return rest == 0 ? tr.tr("litematica_materialshare.stacks.exact", stacks) : tr.tr("litematica_materialshare.stacks.rest", stacks, rest);
    }
}
