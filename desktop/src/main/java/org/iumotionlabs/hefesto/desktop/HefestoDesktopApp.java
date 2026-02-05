package org.iumotionlabs.hefesto.desktop;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iumotionlabs.hefesto.desktop.framework.FeatureRegistry;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;
import org.iumotionlabs.hefesto.desktop.shell.ShellView;
import org.iumotionlabs.hefesto.desktop.theme.ThemeManager;

public class HefestoDesktopApp extends Application {

    private static final Logger log = LogManager.getLogger(HefestoDesktopApp.class);
    private FeatureRegistry featureRegistry;

    @Override
    public void start(Stage primaryStage) {
        log.info("Starting HEFESTO Desktop");

        ServiceLocator.initialize();
        I18nService.getInstance().initialize();
        featureRegistry = new FeatureRegistry();
        featureRegistry.discoverAndInitialize();

        var shellView = new ShellView(featureRegistry);
        var scene = new Scene(shellView, 1280, 800);

        ThemeManager.getInstance().apply(scene);

        primaryStage.setTitle("HEFESTO Desktop");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        var iconUrl = getClass().getResource("/icons/hefesto.png");
        if (iconUrl != null) {
            primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }

        primaryStage.show();
        log.info("HEFESTO Desktop started successfully");
    }

    @Override
    public void stop() {
        log.info("Shutting down HEFESTO Desktop");
        if (featureRegistry != null) {
            featureRegistry.shutdownAll();
        }
        ServiceLocator.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
