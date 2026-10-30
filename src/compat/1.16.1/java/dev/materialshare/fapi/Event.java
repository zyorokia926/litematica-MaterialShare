package dev.materialshare.fapi;

/** A list of listeners (the Fabric API events this mod uses, for 1.16 and 1.16.1 whose Fabric API lacks them). */
public final class Event<T> {
    private final java.util.List<T> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void register(T listener) {
        listeners.add(listener);
    }

    public java.util.List<T> listeners() {
        return listeners;
    }
}
