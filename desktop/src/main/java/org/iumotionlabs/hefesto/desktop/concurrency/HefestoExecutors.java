package org.iumotionlabs.hefesto.desktop.concurrency;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class HefestoExecutors {

    private static final Logger log = LogManager.getLogger(HefestoExecutors.class);

    private static final ExecutorService IO_POOL =
        Executors.newVirtualThreadPerTaskExecutor();

    private static final ExecutorService CPU_POOL =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private HefestoExecutors() {}

    public static <T> void runAsync(Supplier<T> supplier, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        IO_POOL.submit(() -> {
            try {
                T result = supplier.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                log.error("Async task failed", e);
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    public static void runOnIoThread(Runnable task) {
        IO_POOL.submit(task);
    }

    public static void runOnCpuThread(Runnable task) {
        CPU_POOL.submit(task);
    }

    public static void shutdown() {
        IO_POOL.shutdownNow();
        CPU_POOL.shutdownNow();
    }
}
