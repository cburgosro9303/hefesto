package org.iumotionlabs.hefesto.desktop.execution;

import javafx.scene.Node;

import java.util.function.BooleanSupplier;

public record WizardStep(
    String title,
    Node content,
    BooleanSupplier isValid
) {}
