package com.projectpilot.core;

import javafx.scene.Node;

import java.util.EnumMap;
import java.util.function.Supplier;

public class Router {
    private final EnumMap<PageId, Supplier<Node>> routes = new EnumMap<>(PageId.class);

    public void register(PageId id, Supplier<Node> factory) {
        routes.put(id, factory);
    }

    public Node navigate(PageId id) {
        Supplier<Node> factory = routes.get(id);
        if (factory == null) throw new IllegalStateException("No route registered for: " + id);
        return factory.get();
    }
}
