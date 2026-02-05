module org.iumotionlabs.hefesto.desktop {
    requires org.iumotionlabs.hefesto;
    requires org.iumotionlabs.hefesto.desktop.api;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.logging.log4j;
    requires java.prefs;
    requires tools.jackson.databind;
    requires static lombok;

    uses org.iumotionlabs.hefesto.desktop.api.feature.FeatureProvider;

    provides org.iumotionlabs.hefesto.desktop.api.feature.FeatureProvider
        with org.iumotionlabs.hefesto.desktop.feature.portinfo.PortInfoFeatureProvider,
             org.iumotionlabs.hefesto.desktop.feature.procwatch.ProcWatchFeatureProvider,
             org.iumotionlabs.hefesto.desktop.feature.tools.ToolsFeatureProvider;

    opens org.iumotionlabs.hefesto.desktop to javafx.fxml;
    opens org.iumotionlabs.hefesto.desktop.shell to javafx.fxml;
    opens org.iumotionlabs.hefesto.desktop.preferences to javafx.fxml;
    opens org.iumotionlabs.hefesto.desktop.feature.portinfo.view to javafx.fxml;
    opens org.iumotionlabs.hefesto.desktop.feature.procwatch.view to javafx.fxml;
    opens org.iumotionlabs.hefesto.desktop.feature.tools.view to javafx.fxml;
    opens org.iumotionlabs.hefesto.desktop.dashboard to javafx.fxml;
    opens org.iumotionlabs.hefesto.desktop.execution to javafx.fxml;
    opens org.iumotionlabs.hefesto.desktop.observability to javafx.fxml;

    exports org.iumotionlabs.hefesto.desktop;
    exports org.iumotionlabs.hefesto.desktop.mvvm;
    exports org.iumotionlabs.hefesto.desktop.event;
    exports org.iumotionlabs.hefesto.desktop.concurrency;
    exports org.iumotionlabs.hefesto.desktop.cache;
    exports org.iumotionlabs.hefesto.desktop.theme;
    exports org.iumotionlabs.hefesto.desktop.i18n;
    exports org.iumotionlabs.hefesto.desktop.controls;
    exports org.iumotionlabs.hefesto.desktop.chart;
    exports org.iumotionlabs.hefesto.desktop.dashboard;
    exports org.iumotionlabs.hefesto.desktop.framework;
    exports org.iumotionlabs.hefesto.desktop.execution;
    exports org.iumotionlabs.hefesto.desktop.observability;
    exports org.iumotionlabs.hefesto.desktop.preferences;
    exports org.iumotionlabs.hefesto.desktop.audit;
    exports org.iumotionlabs.hefesto.desktop.shell;
    exports org.iumotionlabs.hefesto.desktop.feature.portinfo;
    exports org.iumotionlabs.hefesto.desktop.feature.portinfo.view;
    exports org.iumotionlabs.hefesto.desktop.feature.procwatch;
    exports org.iumotionlabs.hefesto.desktop.feature.procwatch.view;
    exports org.iumotionlabs.hefesto.desktop.feature.tools;
    exports org.iumotionlabs.hefesto.desktop.feature.tools.view;
}
