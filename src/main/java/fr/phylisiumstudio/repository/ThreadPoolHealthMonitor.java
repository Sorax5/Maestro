package fr.phylisiumstudio.repository;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Health monitor for thread pools.
 *
 * <p>This class provides monitoring capabilities for {@link AbstractGsonRepository} thread pools,
 * allowing you to track metrics, detect anomalies, and receive alerts when the pool reaches
 * critical thresholds.</p>
 *
 * <strong>Use Case:</strong> Production systems with high-throughput batch operations.
 */
public class ThreadPoolHealthMonitor {

    /** Thresholds for alerting on thread pool health. */
    public static class Thresholds {
        private int queueWarning = 50;
        private int queueCritical = 100;
        private double activeThreadRatio = 0.8;
        private long idleTimeoutMs = 60_000L;
    }

    /** Metrics snapshot for the thread pool at a given point in time. */
    public static class MetricsSnapshot {
        @Getter
        private final long timestamp;
        @Getter
        private final int queueSize;
        @Getter
        private final int activeThreads;
        @Getter
        private final int completedTasks;
        private final long activeThreadIdleTimeNanos;

        public MetricsSnapshot(
                long timestamp,
                int queueSize,
                int activeThreads,
                int completedTasks,
                long activeThreadIdleTimeNanos) {
            this.timestamp = timestamp;
            this.queueSize = queueSize;
            this.activeThreads = activeThreads;
            this.completedTasks = completedTasks;
            this.activeThreadIdleTimeNanos = activeThreadIdleTimeNanos;
        }

        public long getIdleTimeNanosPerThread() {
            if (activeThreads <= 0) return Long.MAX_VALUE;
            return activeThreadIdleTimeNanos / activeThreads;
        }

        @Override
        public String toString() {
            return "ThreadPoolHealthMonitor.MetricsSnapshot{" +
                    "timestamp=" + timestamp +
                    ", queueSize=" + queueSize +
                    ", activeThreads=" + activeThreads +
                    ", completedTasks=" + completedTasks +
                    ", idleTimePerThread=" + getIdleTimeNanosPerThread() + "ns" +
                    '}';
        }
    }

    private final Logger logger = Logger.getLogger(ThreadPoolHealthMonitor.class.getName());
    private final Thresholds thresholds;
    private final List<Runnable> alertListeners = new ArrayList<>();
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private volatile boolean monitoringEnabled = true;

    /**
     * Create a health monitor with default thresholds.
     */
    public ThreadPoolHealthMonitor() {
        this(new Thresholds());
    }

    /**
     * Create a health monitor with custom thresholds.
     *
     * @param thresholds alerting thresholds to use
     */
    public ThreadPoolHealthMonitor(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    /**
     * Check if the thread pool is healthy based on current metrics.
     *
     * <p>Returns true if no critical thresholds are exceeded:</p>
     * <ul>
     *   <li>Queue size is below critical threshold</li>
     *   <li>Active thread ratio is not above warning threshold</li>
     * </ul>
     *
     * @param queueSize     current queue size
     * @param activeThreads current number of active threads
     * @param maxThreads    maximum number of threads in the pool
     * @return true if healthy, false otherwise
     */
    public boolean isHealthy(int queueSize, int activeThreads, int maxThreads) {
        return queueSize < thresholds.queueCritical &&
                (maxThreads <= 0 || (double) activeThreads / maxThreads < thresholds.activeThreadRatio);
    }

    /**
     * Check health and get detailed status.
     *
     * @param queueSize     current queue size
     * @param activeThreads current number of active threads
     * @param maxThreads    maximum number of threads in the pool
     * @return HealthStatus describing the current state
     */
    public HealthStatus checkHealth(int queueSize, int activeThreads, int maxThreads) {
        var status = new HealthStatus();
        status.setStatus(HealthStatus.Status.HEALTHY);

        if (queueSize >= thresholds.queueCritical) {
            status.setStatus(HealthStatus.Status.CRITICAL);
            status.queueWarningMessage = "Queue size critical: " + queueSize;
            triggerAlert("CRITICAL: Thread pool queue overflow detected", queueSize);
        } else if (queueSize >= thresholds.queueWarning) {
            status.setStatus(HealthStatus.Status.WARNING);
            status.queueWarningMessage = "Queue size warning: " + queueSize;
        }

        double activeRatio = maxThreads > 0 ? (double) activeThreads / maxThreads : 1.0;
        if (activeRatio >= thresholds.activeThreadRatio) {
            status.setStatus(HealthStatus.Status.CRITICAL);
            status.threadWarningMessage = "Too many threads active: " + activeThreads + "/" + maxThreads;
            triggerAlert("CRITICAL: Thread pool saturated", activeThreads, maxThreads);
        } else if (activeRatio >= 0.5 && status.getStatus() == HealthStatus.Status.HEALTHY) {
            status.setStatus(HealthStatus.Status.WARNING);
        }

        status.completedTasksThisWindow = completedTasks.getAndSet(0);

        return status;
    }

    /**
     * Record a completed task.
     */
    public void onTaskCompleted() {
        completedTasks.incrementAndGet();
    }

    /**
     * Add a listener that will be called when an alert is triggered.
     */
    public void addAlertListener(Runnable listener) {
        alertListeners.add(listener);
    }

    /**
     * Trigger an alert and notify all listeners.
     */
    private void triggerAlert(String message, Object... args) {
        if (!monitoringEnabled) return;

        var sb = new StringBuilder(message);
        for (Object arg : args) {
            sb.append(" ").append(arg);
        }
        logger.warning(sb.toString());

        for (var listener : alertListeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                logger.log(java.util.logging.Level.SEVERE, "Alert listener failed", e);
            }
        }
    }

    /**
     * Enable or disable monitoring.
     */
    public void enableMonitoring() {
        this.monitoringEnabled = true;
        logger.info("ThreadPoolHealthMonitor enabled");
    }

    public void disableMonitoring() {
        this.monitoringEnabled = false;
        logger.info("ThreadPoolHealthMonitor disabled");
    }

    /**
     * Health status with detailed information.
     */
    public static class HealthStatus {
        @Setter
        @Getter
        private Status status;
        @Getter
        private String queueWarningMessage;
        @Getter
        private String threadWarningMessage;

        public int completedTasksThisWindow = 0;

        public enum Status { HEALTHY, WARNING, CRITICAL }

    }
}