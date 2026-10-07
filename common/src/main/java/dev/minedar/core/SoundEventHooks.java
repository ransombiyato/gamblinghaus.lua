package dev.minedar.core;

/**
 * Hook points for scanner audio (section 52). MiNEDAR never creates or
 * synthesises audio assets; these are interfaces the loader adapter can bind to
 * whatever sound events are available, so real assets can be dropped in later
 * without touching scan logic.
 */
public final class SoundEventHooks {

    public enum Event {
        SCAN_START,
        CONTINUOUS_SCANNING,
        BURST_SCAN,
        DOT_IMPACT,
        RADIUS_INCREASE,
        RADIUS_DECREASE,
        EQUIP,
        UNEQUIP
    }

    /** Adapter side that actually plays a bound sound, if any. */
    public interface Sink {
        void play(Event event);
    }

    private Sink sink;

    public void bind(Sink sink) {
        this.sink = sink;
    }

    /** Fires an event; a no-op when no assets are bound, which is the default. */
    public void fire(Event event) {
        if (sink != null) {
            sink.play(event);
        }
    }

    public boolean hasSink() {
        return sink != null;
    }
}
