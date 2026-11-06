package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/**
 * {@link InstanceLocks} lets one request at a time work on a talk, a speaker or a slot.
 */
class InstanceLocksTest {

    private final InstanceLocks locks = new InstanceLocks();

    @Test
    void workOnOneTalkRunsOneAtATime() throws Exception {
        assertEquals(1, mostAtOnce(work -> locks.onTalk(42, work)));
    }

    @Test
    void workOnOneSpeakerRunsOneAtATime() throws Exception {
        assertEquals(1, mostAtOnce(work -> locks.onSpeaker(7, work)));
    }

    @Test
    void workOnOneSlotRunsOneAtATime() throws Exception {
        assertEquals(1, mostAtOnce(work -> locks.onSlot("S4", work)));
    }

    @Test
    void workOnATalkAndASpeakerRunsOneAtATimeWithWorkOnTheSpeaker() throws Exception {
        AtomicInteger turn = new AtomicInteger();
        assertEquals(1, mostAtOnce(work -> turn.incrementAndGet() % 2 == 0
                ? locks.onTalkAndSpeaker(turn.get(), 7, work)
                : locks.onSpeaker(7, work)));
    }

    @Test
    void theWorksAnswerIsReturned() {
        assertEquals("done", locks.onTalkAndSpeaker(1, 2, () -> "done"));
    }

    /** Runs 50 pieces of work at once through {@code runner} and returns how many ran at the same time at most. */
    private static int mostAtOnce(Runner runner) throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> runner.run(() -> {
                most.accumulateAndGet(running.incrementAndGet(), Math::max);
                sleep();
                running.decrementAndGet();
                return 0;
            }));
        }
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                f.get();
            }
        }
        return most.get();
    }

    private static void sleep() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Runner {
        int run(Supplier<Integer> work);
    }
}
