package fr.phylisiumstudio.event;

import fr.phylisiumstudio.annotation.ActionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
    }

    @Nested
    class Registration {

        @Test
        void registerActions_handlerIsInvoked() {
            var handler = new ValueHandler();
            bus.registerActions(handler);

            var args = new EventBus.Arguments(this);
            args.register("value", 42);
            bus.execute("value-event", args);

            assertEquals(List.of(42), handler.values);
        }

        @Test
        void unregisterActions_handlerIsNoLongerInvoked() {
            var handler = new ValueHandler();
            bus.registerActions(handler);
            bus.unregisterActions(handler);

            var args = new EventBus.Arguments(this);
            args.register("value", 1);
            bus.execute("value-event", args);

            assertTrue(handler.values.isEmpty(),
                    "Handler should not be called after unregistration");
        }

        @Test
        void unregisterActions_removesEventKeyWhenNoHandlersLeft() {
            var handler = new ValueHandler();
            bus.registerActions(handler);
            assertTrue(bus.hasAction("value-event"));

            bus.unregisterActions(handler);
            assertFalse(bus.hasAction("value-event"),
                    "Event key should be removed when its last handler is unregistered");
        }

        @Test
        void registerActions_sameHandlerTwice_doesNotDuplicateCalls() {
            var handler = new ValueHandler();
            bus.registerActions(handler);
            bus.registerActions(handler);

            var args = new EventBus.Arguments(this);
            args.register("value", 7);
            bus.execute("value-event", args);

            assertEquals(2, handler.values.size());
        }

        @Test
        void multipleHandlers_sameEvent_allInvoked() {
            var h1 = new ValueHandler();
            var h2 = new ValueHandler();
            bus.registerActions(h1);
            bus.registerActions(h2);

            var args = new EventBus.Arguments(this);
            args.register("value", 5);
            bus.execute("value-event", args);

            assertEquals(List.of(5), h1.values);
            assertEquals(List.of(5), h2.values);
        }

        @Test
        void execute_noHandlersRegistered_doesNotThrow() {
            assertDoesNotThrow(() ->
                    bus.execute("unknown-event", new EventBus.Arguments(this)));
        }
    }

    @Nested
    class MiddlewareTests {

        @Test
        void middlewareOrder_isOuterToInner() {
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

            bus.registerActions(new RecordingHandler(calls));
            bus.execute("demo", new EventBus.Arguments(this));

            assertEquals(List.of(
                    "mw1-before", "mw2-before", "handler", "mw2-after", "mw1-after"
            ), calls);
        }

        @Test
        void middleware_shortCircuit_preventsHandlerExecution() {
            var handler = new ValueHandler();
            bus.registerActions(handler);

            bus.addMiddleware((event, args, next) -> { /* intentionally blocked */ });

            var args = new EventBus.Arguments(this);
            args.register("value", 1);
            bus.execute("value-event", args);

            assertTrue(handler.values.isEmpty(),
                    "Handler must not be called when middleware short-circuits");
        }

        @Test
        void removeMiddleware_middlewareNoLongerRuns() {
            var calls = new ArrayList<String>();
            Middleware mw = (event, args, next) -> {
                calls.add("mw");
                next.run();
            };

            bus.addMiddleware(mw);
            bus.removeMiddleware(mw);
            bus.registerActions(new RecordingHandler(calls));
            bus.execute("demo", new EventBus.Arguments(this));

            assertFalse(calls.contains("mw"), "Removed middleware must not run");
        }

        @Test
        void clearMiddlewares_allMiddlewaresRemoved() {
            bus.addMiddleware((e, a, n) -> n.run());
            bus.addMiddleware((e, a, n) -> n.run());
            bus.clearMiddlewares();

            assertEquals(0, bus.getMiddlewaresCount());
        }

        @Test
        void getMiddlewaresCount_reflectsAddAndRemove() {
            assertEquals(0, bus.getMiddlewaresCount());
            Middleware mw = (e, a, n) -> n.run();
            bus.addMiddleware(mw);
            assertEquals(1, bus.getMiddlewaresCount());
            bus.removeMiddleware(mw);
            assertEquals(0, bus.getMiddlewaresCount());
        }
    }

    @Nested
    class ActionRegistry {

        @Test
        void hasAction_trueAfterRegister_falseAfterRemove() {
            bus.registerActions(new ValueHandler());
            assertTrue(bus.hasAction("value-event"));
            bus.removeAction("value-event");
            assertFalse(bus.hasAction("value-event"));
        }

        @Test
        void getActionNames_containsRegisteredEvents() {
            bus.registerActions(new ValueHandler());
            assertTrue(bus.getActionNames().contains("value-event"));
        }

        @Test
        void getActionsCount_reflectsDistinctEvents() {
            bus.registerActions(new ValueHandler());
            bus.registerActions(new RecordingHandler(new ArrayList<>()));
            // value-event + demo = 2 distinct keys
            assertEquals(2, bus.getActionsCount());
        }

        @Test
        void clearActions_removesEverything() {
            bus.registerActions(new ValueHandler());
            bus.clearActions();
            assertEquals(0, bus.getActionsCount());
        }

        @Test
        void removeAction_handlerNoLongerCalled() {
            var handler = new ValueHandler();
            bus.registerActions(handler);
            bus.removeAction("value-event");

            var args = new EventBus.Arguments(this);
            args.register("value", 1);
            bus.execute("value-event", args);

            assertTrue(handler.values.isEmpty());
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void setErrorHandler_receivesExceptionFromHandler() {
            var captured = new AtomicReference<Throwable>();
            bus.setErrorHandler(captured::set);

            bus.registerActions(new ThrowingHandler());
            bus.execute("throw-event", new EventBus.Arguments(this));

            assertNotNull(captured.get(), "Error handler should receive the exception");
        }
    }

    @Nested
    class EventSourcing {

        @Test
        void eventSourcing_recordsOnlyWhenEnabled() {
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
            var journal = new EventJournal();
            bus.enableEventSourcing(journal);
            assertNotNull(bus.getEventJournal());

            bus.disableEventSourcing();
            assertNull(bus.getEventJournal());
        }

        @Test
        void replayAll_reinvokesHandlersWithRecordedArguments() {
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

            bus.replayAll("value-event");

            assertEquals(List.of(7, 9, 7, 9), handler.values);
        }

        @Test
        void replay_reinvokesHandlersWithRecordedArgumentsInRange() {
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
            var from = records.get(0).timestamp;
            var to = records.get(1).timestamp.minusNanos(1);

            bus.replay("value-event", from, to);

            assertEquals(List.of(11, 22, 11), handler.values);
        }

        @Test
        void replay_usesRecordedSnapshot_notMutatedArgumentsInstance() {
            var journal = new EventJournal();
            bus.enableEventSourcing(journal);
            var handler = new ValueHandler();
            bus.registerActions(handler);

            var args = new EventBus.Arguments(this);
            args.register("value", 1);
            bus.execute("value-event", args);

            args.register("value", 99);

            bus.replayAll("value-event");

            assertEquals(List.of(1, 1), handler.values);
        }

        @Test
        void replayAll_withoutEventSourcing_throwsIllegalState() {
            assertThrows(IllegalStateException.class,
                    () -> bus.replayAll("value-event"),
                    "replayAll() must throw when event sourcing is not enabled");
        }

        @Test
        void replay_withoutEventSourcing_throwsIllegalState() {
            assertThrows(IllegalStateException.class,
                    () -> bus.replay("value-event", Instant.now(), Instant.now()),
                    "replay() must throw when event sourcing is not enabled");
        }
    }

    @Nested
    class ArgumentsTests {

        @Test
        void get_returnsRegisteredValue() {
            var args = new EventBus.Arguments(this);
            args.register("key", "hello");
            assertEquals("hello", args.get("key", String.class));
        }

        @Test
        void get_missingKey_throwsIllegalArgument() {
            var args = new EventBus.Arguments(this);
            assertThrows(IllegalArgumentException.class,
                    () -> args.get("missing", String.class));
        }

        @Test
        void get_wrongType_throwsClassCast() {
            var args = new EventBus.Arguments(this);
            args.register("key", 42);
            assertThrows(ClassCastException.class,
                    () -> args.get("key", String.class));
        }

        @Test
        void getList_returnsTypedList() {
            var args = new EventBus.Arguments(this);
            args.register("items", List.of(1, 2, 3));
            assertEquals(List.of(1, 2, 3), args.getList("items", Integer.class));
        }

        @Test
        void getList_notAList_throwsClassCast() {
            var args = new EventBus.Arguments(this);
            args.register("key", "not-a-list");
            assertThrows(ClassCastException.class,
                    () -> args.getList("key", String.class));
        }

        @Test
        void getOptional_presentValue_returnsNonEmpty() {
            var args = new EventBus.Arguments(this);
            args.register("key", "value");
            assertTrue(args.getOptional("key", String.class).isPresent());
            assertEquals("value", args.getOptional("key", String.class).get());
        }

        @Test
        void getOptional_missingKey_returnsEmpty() {
            var args = new EventBus.Arguments(this);
            assertTrue(args.getOptional("missing", String.class).isEmpty());
        }

        @Test
        void getOptionalList_presentList_returnsNonEmpty() {
            var args = new EventBus.Arguments(this);
            args.register("items", List.of("a", "b"));
            var result = args.getOptionalList("items", String.class);
            assertTrue(result.isPresent());
            assertEquals(List.of("a", "b"), result.get());
        }

        @Test
        void getOptionalList_missingKey_returnsEmpty() {
            var args = new EventBus.Arguments(this);
            assertTrue(args.getOptionalList("missing", String.class).isEmpty());
        }

        @Test
        void getOrDefault_missingKey_returnsDefault() {
            var args = new EventBus.Arguments(this);
            assertEquals("default", args.getOrDefault("missing", String.class, "default"));
        }

        @Test
        void getOrDefault_presentKey_returnsValue() {
            var args = new EventBus.Arguments(this);
            args.register("key", "real");
            assertEquals("real", args.getOrDefault("key", String.class, "default"));
        }

        @Test
        void getListOrDefault_missingKey_returnsDefault() {
            var args = new EventBus.Arguments(this);
            var def = List.of("x");
            assertEquals(def, args.getListOrDefault("missing", String.class, def));
        }

        @Test
        void containsKey_trueWhenPresent_falseWhenAbsent() {
            var args = new EventBus.Arguments(this);
            args.register("present", 1);
            assertTrue(args.containsKey("present"));
            assertFalse(args.containsKey("absent"));
        }

        @Test
        void getKeysAndTypes_returnsCorrectMapping() {
            var args = new EventBus.Arguments(this);
            args.register("count", 5);
            args.register("label", "hello");
            var types = args.getKeysAndTypes();
            assertEquals(Integer.class, types.get("count"));
            assertEquals(String.class, types.get("label"));
        }

        @Test
        void getEmitterAs_correctType_returnsEmitter() {
            var outerThis = EventBusTest.this;
            var args = new EventBus.Arguments(outerThis);
            assertSame(outerThis, args.getEmitterAs(EventBusTest.class));
        }

        @Test
        void getEmitterAs_wrongType_throwsClassCast() {
            var args = new EventBus.Arguments(EventBusTest.this);
            assertThrows(ClassCastException.class,
                    () -> args.getEmitterAs(String.class));
        }
    }

    private static class RecordingHandler {
        private final List<String> calls;

        RecordingHandler(List<String> calls) {
            this.calls = calls;
        }

        @ActionHandler(event = "demo")
        public void onDemo(EventBus.Arguments args) {
            calls.add("handler");
        }
    }

    private static class ValueHandler {
        final List<Integer> values = new ArrayList<>();

        @ActionHandler(event = "value-event")
        public void onValue(EventBus.Arguments args) {
            values.add(args.get("value", Integer.class));
        }
    }

    private static class ThrowingHandler {
        @ActionHandler(event = "throw-event")
        public void onThrow(EventBus.Arguments args) {
            throw new RuntimeException("intentional error");
        }
    }
}