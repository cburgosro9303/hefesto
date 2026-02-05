module org.iumotionlabs.hefesto {
    // Required modules
    requires tools.jackson.databind;
    requires tools.jackson.dataformat.yaml;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    // Static (compile-time only)
    requires static lombok;

    // Exported packages (public API)
    exports org.iumotionlabs.hefesto;
    exports org.iumotionlabs.hefesto.command;
    exports org.iumotionlabs.hefesto.core.config;
    exports org.iumotionlabs.hefesto.core.context;
    exports org.iumotionlabs.hefesto.core.port.input;
    exports org.iumotionlabs.hefesto.core.port.output;
    exports org.iumotionlabs.hefesto.core.logging;
    exports org.iumotionlabs.hefesto.help;

    // PortInfo feature exports
    exports org.iumotionlabs.hefesto.feature.portinfo;
    exports org.iumotionlabs.hefesto.feature.portinfo.model;
    exports org.iumotionlabs.hefesto.feature.portinfo.service;
    exports org.iumotionlabs.hefesto.feature.portinfo.output;
    exports org.iumotionlabs.hefesto.feature.portinfo.parser;

    // ProcWatch feature exports
    exports org.iumotionlabs.hefesto.feature.procwatch;
    exports org.iumotionlabs.hefesto.feature.procwatch.model;
    exports org.iumotionlabs.hefesto.feature.procwatch.service;
    exports org.iumotionlabs.hefesto.feature.procwatch.sampler;

    // JMX for JVM monitoring
    requires java.management;
    requires java.management.rmi;

    // Open for reflection (Jackson serialization)
    opens org.iumotionlabs.hefesto.core.config to tools.jackson.databind;
    opens org.iumotionlabs.hefesto.feature.portinfo.model to tools.jackson.databind;
    opens org.iumotionlabs.hefesto.feature.procwatch.model to tools.jackson.databind;
}
