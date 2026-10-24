package dev.materialshare.client;

/** Implemented (by mixin) by Litematica's material list row so the list can hand it mouse-wheel events. */
public interface RowScroll {
    boolean materialshare$scroll(int mouseX, int mouseY, double amount);
}
