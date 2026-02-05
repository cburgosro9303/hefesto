package org.iumotionlabs.hefesto.desktop.api.action;

import java.util.List;

public record ParameterDescriptor(
    String name,
    String labelKey,
    ParameterType type,
    boolean required,
    Object defaultValue,
    List<String> allowedValues
) {
    public enum ParameterType {
        STRING, INTEGER, BOOLEAN, CHOICE
    }
}
