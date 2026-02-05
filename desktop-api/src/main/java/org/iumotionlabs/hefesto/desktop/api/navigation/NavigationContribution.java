package org.iumotionlabs.hefesto.desktop.api.navigation;

public record NavigationContribution(
    String id,
    String labelKey,
    String iconLiteral,
    String parentId,
    int order,
    Class<?> viewClass
) {}
