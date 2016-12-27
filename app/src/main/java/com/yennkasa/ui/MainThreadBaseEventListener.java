package com.yennkasa.ui;

import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;

/**
 * @author aminu on 7/9/2016.
 */
public abstract class MainThreadBaseEventListener implements EventBus.EventsListener {
    @Override
    public final int threadMode() {
        return EventBus.MAIN;
    }

    @Override
    public final void onEvent(EventBus yourBus, Event event) {
        try {
            handleEvent(event);
        } finally {
            if (event.isSticky()) {
                yourBus.removeStickyEvent(event);
            } else {
                event.recycle();
            }
        }
    }

    protected abstract void handleEvent(Event event);

    @Override
    public final boolean sticky() {
        return true;
    }
}
