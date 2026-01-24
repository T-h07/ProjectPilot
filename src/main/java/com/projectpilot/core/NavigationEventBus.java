package com.projectpilot.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NavigationEventBus {
    private final List<Consumer<PageId>> listeners = new ArrayList<>();

    public void onNavigate(Consumer<PageId> listener) {
        listeners.add(listener);
    }

    public void navigate(PageId pageId) {
        for (var l : listeners) l.accept(pageId);
    }
}
