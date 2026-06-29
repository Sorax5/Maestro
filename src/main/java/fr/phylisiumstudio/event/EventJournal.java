package fr.phylisiumstudio.event;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * EventJournal persists events for event sourcing.
 *
 * <p>Allows replaying events, auditing, and temporal debugging.
 *
 * <p>Example:
 * <pre>
 * EventJournal journal = new EventJournal();
 * eventBus.enableEventSourcing(journal);
 *
 * // Execute events normally - they get recorded
 * eventBus.execute("onDamage", args);
 *
 * // Later, replay events from a time range
 * eventBus.replay("Combat", startTime, endTime);
 * </pre>
 */
public class EventJournal {
    private final List<EventRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Supplier<Instant> clock;

    public EventJournal() {
        this(Instant::now);
    }

    public EventJournal(@NotNull Supplier<Instant> clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    private List<EventRecord> snapshotRecords() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    /**
     * Record an event in the journal.
     *
     * @param eventName the name of the event
     * @param args the arguments passed to the event
     */
    void recordEvent(@NotNull String eventName, @NotNull EventBus.Arguments args) {
        records.add(new EventRecord(eventName, args.getEmitter(), args.snapshotValues(), clock.get()));
    }

    /**
     * Get all events recorded in the journal.
     *
     * @return a list of event records
     */
    public List<EventRecord> getAllEvents() {
        return snapshotRecords();
    }

    /**
     * Get events for a specific event name.
     *
     * @param eventName the name of the event to filter by
     * @return a list of matching event records
     */
    public List<EventRecord> getEventsByName(@NotNull String eventName) {
        return snapshotRecords().stream()
                .filter(r -> r.eventName.equals(eventName))
                .toList();
    }

    /**
     * Get events in a time range.
     *
     * @param from the start time
     * @param to the end time
     * @return a list of event records within the time range
     */
    public List<EventRecord> getEventsByTimeRange(@NotNull Instant from, @NotNull Instant to) {
        return snapshotRecords().stream()
                .filter(r -> !r.timestamp.isBefore(from) && !r.timestamp.isAfter(to))
                .toList();
    }

    /**
     * Get events by name and time range.
     *
     * @param eventName the name of the event to filter by
     * @param from the start time
     * @param to the end time
     * @return a list of matching event records
     */
    public List<EventRecord> getEvents(@NotNull String eventName, @NotNull Instant from, @NotNull Instant to) {
        return snapshotRecords().stream()
                .filter(r -> r.eventName.equals(eventName) &&
                           !r.timestamp.isBefore(from) &&
                           !r.timestamp.isAfter(to))
                .toList();
    }

    /**
     * Get the total count of events.
     *
     * @return the count of events
     */
    public int getEventCount() {
        return records.size();
    }

    /**
     * Clear all events from the journal.
     */
    public void clear() {
        records.clear();
    }

    /**
     * Represents a recorded event in the journal.
     */
    public static class EventRecord {
        public final String eventName;
        public final Object emitter;
        public final Map<String, Object> argumentsSnapshot;
        public final Instant timestamp;

        EventRecord(@NotNull String eventName, @NotNull Object emitter, @NotNull Map<String, Object> argumentsSnapshot, @NotNull Instant timestamp) {
            this.eventName = eventName;
            this.emitter = emitter;
            this.argumentsSnapshot = Map.copyOf(argumentsSnapshot);
            this.timestamp = timestamp;
        }

        public EventBus.Arguments toArguments() {
            EventBus.Arguments arguments = new EventBus.Arguments(emitter);
            for (Map.Entry<String, Object> entry : argumentsSnapshot.entrySet()) {
                arguments.register(entry.getKey(), entry.getValue());
            }
            return arguments;
        }

        @Override
        public String toString() {
            return String.format("EventRecord{eventName='%s', timestamp=%s}", eventName, timestamp);
        }
    }
}

