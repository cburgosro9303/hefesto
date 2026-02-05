package org.iumotionlabs.hefesto.desktop.api.action;

import java.util.List;
import java.util.Map;

public interface ActionHandler {

    ActionResult execute(ActionMonitor monitor, Map<String, Object> params);

    default List<ParameterDescriptor> parameters() {
        return List.of();
    }
}
