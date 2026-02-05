package org.iumotionlabs.hefesto.desktop.shell;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iumotionlabs.hefesto.desktop.api.navigation.NavigationContribution;
import org.iumotionlabs.hefesto.desktop.framework.FeatureRegistry;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

import java.lang.reflect.Constructor;
import java.util.List;

public class ShellView extends BorderPane {

    private static final Logger log = LogManager.getLogger(ShellView.class);

    private final ShellViewModel viewModel;
    private final SidebarView sidebar;
    private final TabContainerView tabContainer;
    private final BreadcrumbBar breadcrumbBar;
    private final StatusBarView statusBar;
    private final StackPane contentStack;
    private CommandPaletteView commandPalette;

    public ShellView(FeatureRegistry featureRegistry) {
        getStyleClass().add("shell");
        this.viewModel = new ShellViewModel(featureRegistry);

        sidebar = new SidebarView();
        sidebar.setNavigationItems(viewModel.getNavigationItems());
        sidebar.setOnNavigate(this::handleNavigation);

        breadcrumbBar = new BreadcrumbBar();
        tabContainer = new TabContainerView();
        statusBar = new StatusBarView();

        var centerArea = new VBox(breadcrumbBar, tabContainer);
        VBox.setVgrow(tabContainer, Priority.ALWAYS);

        contentStack = new StackPane(centerArea);

        setLeft(sidebar);
        setCenter(contentStack);
        setBottom(statusBar);

        // Bindings
        viewModel.statusTextProperty().addListener((_, _, text) -> statusBar.setStatusText(text));
        viewModel.runningTaskCountProperty().addListener((_, _, count) -> statusBar.setTaskCount(count.intValue()));

        setupCommandPalette(featureRegistry);
        setupKeyBindings();

        // Auto-select first nav item
        sidebar.selectFirst();
    }

    private void handleNavigation(NavigationContribution item) {
        viewModel.navigateTo(item);
        breadcrumbBar.setPath(List.of("HEFESTO", I18nService.getInstance().t(item.labelKey())));

        try {
            Node view = instantiateView(item.viewClass());
            tabContainer.openTab(item, view);
        } catch (Exception e) {
            log.error("Failed to create view for {}", item.id(), e);
        }
    }

    private Node instantiateView(Class<?> viewClass) throws Exception {
        for (Constructor<?> ctor : viewClass.getConstructors()) {
            if (ctor.getParameterCount() == 0) {
                return (Node) ctor.newInstance();
            }
        }
        throw new IllegalStateException("No suitable constructor for " + viewClass.getName());
    }

    private void setupCommandPalette(FeatureRegistry registry) {
        var paletteViewModel = new CommandPaletteViewModel(registry, this::handleNavigation);
        commandPalette = new CommandPaletteView(paletteViewModel);
        commandPalette.setVisible(false);
        commandPalette.setOnClose(() -> commandPalette.setVisible(false));

        contentStack.getChildren().add(commandPalette);

        viewModel.commandPaletteVisibleProperty().addListener((_, _, visible) -> {
            commandPalette.setVisible(visible);
            if (visible) commandPalette.focus();
        });
    }

    private void setupKeyBindings() {
        var ctrlK = new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN);
        sceneProperty().addListener((_, _, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(ctrlK, viewModel::toggleCommandPalette);
            }
        });
    }
}
