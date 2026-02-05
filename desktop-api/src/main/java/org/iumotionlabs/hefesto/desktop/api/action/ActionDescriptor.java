package org.iumotionlabs.hefesto.desktop.api.action;

public record ActionDescriptor(
    String id,
    String labelKey,
    String descriptionKey,
    String iconLiteral,
    String category,
    ActionType type,
    Class<?> actionHandlerClass
) {}
