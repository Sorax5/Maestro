package fr.phylisiumstudio.event;

import fr.phylisiumstudio.annotation.ActionHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventBusTest {

    @Test
    void middlewareOrder_isOuterToInner() {
        var bus = new EventBus();
        var calls = new ArrayList<String>();

        bus.addMiddleware((event, args, next) -> {
            calls.add("mw1-before");
            next.run();
            calls.add("mw1-after");
        });

        bus.addMiddleware((event, args, next) -> {
            calls.add("mw2-before");
            next.run();
            calls.add("mw2-after");
        });

        var handler = new RecordingHandler(calls);
        bus.registerActions(handler);

        bus.execute("demo", new EventBus.Arguments(this));

        assertEquals(List.of(
                "mw1-before",
                "mw2-before",
                "handler",
                "mw2-after",
                "mw1-after"
        ), calls);
    }

    @Test
    void eventSourcing_recordsOnlyWhenEnabled() {
        var bus = new EventBus();
        var journal = new EventJournal();

        bus.execute("event", new EventBus.Arguments(this));
        assertEquals(0, journal.getEventCount());

        bus.enableEventSourcing(journal);
        bus.execute("event", new EventBus.Arguments(this));
        assertEquals(1, journal.getEventCount());

        bus.disableEventSourcing();
        bus.execute("event", new EventBus.Arguments(this));
        assertEquals(1, journal.getEventCount());
    }

    @Test
    void getEventJournal_returnsNullWhenDisabled() {
        var bus = new EventBus();
        var journal = new EventJournal();

        bus.enableEventSourcing(journal);
        assertNotNull(bus.getEventJournal());

        bus.disableEventSourcing();
        assertNull(bus.getEventJournal());
    }

    @Test
    void replayAll_reinvokesHandlersWithRecordedArguments() {
        var bus = new EventBus();
        var journal = new EventJournal();
        bus.enableEventSourcing(journal);

        var handler = new ValueHandler();
        bus.registerActions(handler);

        var first = new EventBus.Arguments(this);
        first.register("value", 7);
        bus.execute("value-event", first);

        var second = new EventBus.Arguments(this);
        second.register("value", 9);
        bus.execute("value-event", second);

        assertEquals(List.of(7, 9), handler.values);

        bus.replayAll("value-event");

        assertEquals(List.of(7, 9, 7, 9), handler.values);
    }

    @Test
    void replay_reinvokesHandlersWithRecordedArgumentsInRange() {
        var bus = new EventBus();
        var firstRecordedAt = Instant.parse("2026-01-01T00:00:00Z");
        var secondRecordedAt = Instant.parse("2026-01-01T00:00:01Z");
        var clockValues = new ArrayDeque<>(List.of(firstRecordedAt, secondRecordedAt));
        var journal = new EventJournal(() -> {
            var next = clockValues.pollFirst();
            return next != null ? next : secondRecordedAt;
        });
        bus.enableEventSourcing(journal);

        var handler = new ValueHandler();
        bus.registerActions(handler);

        var first = new EventBus.Arguments(this);
        first.register("value", 11);
        bus.execute("value-event", first);

        var second = new EventBus.Arguments(this);
        second.register("value", 22);
        bus.execute("value-event", second);

        var records = journal.getEventsByName("value-event");
        var firstTimestamp = records.get(0).timestamp;
        var replayEnd = records.get(1).timestamp.minusNanos(1);

        bus.replay("value-event", firstTimestamp, replayEnd);

        assertEquals(List.of(11, 22, 11), handler.values);
    }

    @Test
    void replay_usesRecordedSnapshot_notMutatedArgumentsInstance() {
        var bus = new EventBus();
        var journal = new EventJournal();
        bus.enableEventSourcing(journal);

        var handler = new ValueHandler();
        bus.registerActions(handler);

        var args = new EventBus.Arguments(this);
        args.register("value", 1);
        bus.execute("value-event", args);

        // Mutate the same Arguments instance after execution.
        args.register("value", 99);

        bus.replayAll("value-event");

        assertEquals(List.of(1, 1), handler.values);
    }

    private static class RecordingHandler {
        private final List<String> calls;

        private RecordingHandler(List<String> calls) {
            this.calls = calls;
        }

        @ActionHandler(event = "demo")
        public void onDemo(EventBus.Arguments args) {
            calls.add("handler");
        }
    }

    private static class ValueHandler {
        private final List<Integer> values = new ArrayList<>();

        @ActionHandler(event = "value-event")
        public void onValue(EventBus.Arguments args) {
            values.add(args.get("value", Integer.class));
        }
    }
}

