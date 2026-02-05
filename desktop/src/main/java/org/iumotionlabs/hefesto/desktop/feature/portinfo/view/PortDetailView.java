package org.iumotionlabs.hefesto.desktop.feature.portinfo.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.controls.ProcessKillDialog;
import org.iumotionlabs.hefesto.desktop.controls.StatusBadge;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;
import org.iumotionlabs.hefesto.feature.portinfo.model.EnrichedPortBinding;

import java.time.format.DateTimeFormatter;

public class PortDetailView extends VBox {

    private final PortDetailViewModel viewModel = new PortDetailViewModel();
    private final Label titleLabel = new Label("Select a port");
    private final VBox infoSection = new VBox(6);
    private final VBox healthSection = new VBox(6);
    private final VBox securitySection = new VBox(6);
    private final VBox dockerSection = new VBox(6);
    private final TabPane tabs = new TabPane();
    private final Tab dockerTab;
    private final I18nService i18n = I18nService.getInstance();

    public PortDetailView() {
        getStyleClass().add("port-detail-view");
        setSpacing(10);
        setPadding(new Insets(10));
        setMinWidth(300);

        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var infoTab = new Tab("Info", infoSection);
        infoTab.setClosable(false);

        var healthTab = new Tab("Health", healthSection);
        healthTab.setClosable(false);

        var securityTab = new Tab("Security", securitySection);
        securityTab.setClosable(false);

        dockerTab = new Tab(i18n.t("portinfo.docker"), dockerSection);
        dockerTab.setClosable(false);

        tabs.getTabs().addAll(infoTab, healthTab, securityTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        getChildren().addAll(titleLabel, tabs);

        // Health result listener
        viewModel.healthResultProperty().addListener((_, _, result) -> {
            healthSection.getChildren().clear();
            if (result == null) return;

            healthSection.getChildren().addAll(
                new Label("Status: " + (result.isHealthy() ? "Healthy" : "Unhealthy")),
                new Label("Response time: " + result.responseTimeMs() + "ms"),
                new Label("Details: " + result.message())
            );

            // HTTP info
            result.httpInfoOpt().ifPresent(http -> {
                healthSection.getChildren().add(new Separator());
                var statusBadge = new StatusBadge(http.statusFormatted(),
                    http.isSuccess() ? StatusBadge.Severity.SUCCESS : StatusBadge.Severity.WARNING);
                healthSection.getChildren().addAll(
                    createField(i18n.t("portinfo.http.status"), null, statusBadge),
                    createField(i18n.t("portinfo.http.content.type"), http.contentType()),
                    createField(i18n.t("portinfo.http.content.length"), http.contentLengthFormatted()),
                    createField(i18n.t("portinfo.http.response.time"), http.responseTimeMs() + "ms")
                );
            });

            // SSL info
            result.sslInfoOpt().ifPresent(ssl -> {
                healthSection.getChildren().add(new Separator());
                var validBadge = new StatusBadge(ssl.valid() ? "Valid" : "Invalid",
                    ssl.valid() ? StatusBadge.Severity.SUCCESS : StatusBadge.Severity.CRITICAL);
                healthSection.getChildren().add(createField("SSL Certificate", null, validBadge));
                healthSection.getChildren().addAll(
                    createField(i18n.t("portinfo.ssl.issuer"), ssl.issuer()),
                    createField(i18n.t("portinfo.ssl.subject"), ssl.subject()),
                    createField(i18n.t("portinfo.ssl.protocol"), ssl.protocol()),
                    createField(i18n.t("portinfo.ssl.cipher"), ssl.cipherSuite())
                );
                if (ssl.validFrom() != null) {
                    var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    healthSection.getChildren().addAll(
                        createField(i18n.t("portinfo.ssl.valid.from"), ssl.validFrom().format(fmt)),
                        createField(i18n.t("portinfo.ssl.valid.to"), ssl.validTo().format(fmt))
                    );
                    long daysExpiry = ssl.daysUntilExpiry();
                    var expiryBadge = new StatusBadge(daysExpiry + " days",
                        daysExpiry < 0 ? StatusBadge.Severity.CRITICAL :
                        daysExpiry <= 30 ? StatusBadge.Severity.WARNING :
                        StatusBadge.Severity.SUCCESS);
                    healthSection.getChildren().add(
                        createField(i18n.t("portinfo.ssl.days.expiry"), null, expiryBadge));
                }
            });
        });

        // Security flags listener
        viewModel.securityFlagsProperty().addListener((_, _, flags) -> {
            securitySection.getChildren().clear();
            if (flags == null) return;

            for (var flag : flags) {
                var badge = new StatusBadge(flag.severity().name(), switch (flag.severity().name()) {
                    case "CRITICAL" -> StatusBadge.Severity.CRITICAL;
                    case "HIGH" -> StatusBadge.Severity.HIGH;
                    case "WARNING" -> StatusBadge.Severity.WARNING;
                    default -> StatusBadge.Severity.INFO;
                });

                var titleText = new Label(flag.title());
                titleText.setStyle("-fx-font-weight: bold;");
                var categoryLabel = new Label(flag.category().displayName());
                categoryLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.6;");
                var desc = new Label(flag.description());
                desc.setWrapText(true);

                var flagBox = new VBox(2, new HBox(8, badge, titleText), categoryLabel, desc);

                // Recommendation
                if (flag.recommendation() != null && !flag.recommendation().isEmpty()) {
                    var recLabel = new Label(i18n.t("portinfo.security.recommendation") + ": " + flag.recommendation());
                    recLabel.setWrapText(true);
                    recLabel.setStyle("-fx-font-style: italic; -fx-opacity: 0.7;");
                    flagBox.getChildren().add(recLabel);
                }

                flagBox.setPadding(new Insets(4, 0, 4, 0));
                securitySection.getChildren().add(flagBox);
            }
            if (flags.isEmpty()) {
                securitySection.getChildren().add(new Label("No security issues detected"));
            }
        });

        // Docker info listener
        viewModel.dockerInfoProperty().addListener((_, _, docker) -> {
            if (docker != null) {
                if (!tabs.getTabs().contains(dockerTab)) {
                    tabs.getTabs().add(dockerTab);
                }
                dockerSection.getChildren().clear();
                var runningBadge = new StatusBadge(docker.isRunning() ? "Running" : "Stopped",
                    docker.isRunning() ? StatusBadge.Severity.SUCCESS : StatusBadge.Severity.WARNING);
                dockerSection.getChildren().addAll(
                    createField(i18n.t("portinfo.docker.container"), docker.containerName()),
                    createField(i18n.t("portinfo.docker.image"), docker.image()),
                    createField(i18n.t("portinfo.docker.status"), null, runningBadge),
                    createField("Container ID", docker.shortId())
                );
                if (docker.portMappings() != null && !docker.portMappings().isEmpty()) {
                    dockerSection.getChildren().add(new Separator());
                    dockerSection.getChildren().add(new Label(i18n.t("portinfo.docker.ports") + ":"));
                    for (var mapping : docker.portMappings()) {
                        dockerSection.getChildren().add(new Label("  " + mapping.toDisplayString()));
                    }
                }
            } else {
                tabs.getTabs().remove(dockerTab);
            }
        });
    }

    public void showBinding(EnrichedPortBinding binding) {
        viewModel.setBinding(binding);
        if (binding == null) {
            titleLabel.setText("Select a port");
            infoSection.getChildren().clear();
            healthSection.getChildren().clear();
            securitySection.getChildren().clear();
            return;
        }

        titleLabel.setText("Port " + binding.port() + " (" + binding.protocol() + ")");

        infoSection.getChildren().clear();
        infoSection.getChildren().addAll(
            createField("Port", String.valueOf(binding.port())),
            createField("Protocol", binding.protocol()),
            createField("Address", binding.localAddress()),
            createField("State", binding.state()),
            createField("Process", binding.processName()),
            createField("PID", String.valueOf(binding.pid())),
            createField("User", binding.user())
        );

        binding.serviceInfoOpt().ifPresent(si -> {
            infoSection.getChildren().addAll(
                new Separator(),
                createField("Service", si.name()),
                createField("Description", si.description())
            );
        });

        binding.processInfoOpt().ifPresent(pi -> {
            infoSection.getChildren().addAll(
                new Separator(),
                createField("Command", pi.commandLine()),
                createField("Process User", pi.user())
            );
        });

        // Kill process button
        var killBtn = new Button(i18n.t("portinfo.kill.process"));
        killBtn.getStyleClass().add("kill-button");
        killBtn.setOnAction(_ -> ProcessKillDialog.show(binding.pid(), binding.processName()));
        infoSection.getChildren().addAll(new Separator(), killBtn);

        // Health check section
        healthSection.getChildren().clear();
        var checkBtn = new Button("Run Health Check");
        checkBtn.setOnAction(_ -> viewModel.runHealthCheck());
        checkBtn.disableProperty().bind(viewModel.healthCheckRunningProperty());
        var progress = new ProgressIndicator();
        progress.setMaxSize(20, 20);
        progress.visibleProperty().bind(viewModel.healthCheckRunningProperty());
        healthSection.getChildren().addAll(new HBox(8, checkBtn, progress));
    }

    private HBox createField(String label, String value) {
        var labelNode = new Label(label + ":");
        labelNode.setStyle("-fx-font-weight: bold; -fx-min-width: 80;");
        var valueNode = new Label(value != null ? value : "-");
        valueNode.setWrapText(true);
        return new HBox(8, labelNode, valueNode);
    }

    private HBox createField(String label, String value, StatusBadge badge) {
        var labelNode = new Label(label + ":");
        labelNode.setStyle("-fx-font-weight: bold; -fx-min-width: 80;");
        if (value != null) {
            var valueNode = new Label(value);
            valueNode.setWrapText(true);
            return new HBox(8, labelNode, valueNode, badge);
        }
        return new HBox(8, labelNode, badge);
    }
}
