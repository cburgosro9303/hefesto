package org.iumotionlabs.hefesto.desktop.api.action;

public sealed interface ActionResult {

    record Success(String message, Object data) implements ActionResult {}

    record Failure(String error, Throwable cause) implements ActionResult {}

    record Cancelled() implements ActionResult {}
}
