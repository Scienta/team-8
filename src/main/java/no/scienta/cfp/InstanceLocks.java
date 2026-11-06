package no.scienta.cfp;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Lines up, inside this instance, the requests that change one talk, one speaker's load or one slot, before they take
 * a database connection. Without it, a rush on one talk has every connection of the pool of five waiting on the same
 * {@code SELECT … FOR UPDATE}, and every other request waits for a connection. With it, the rush waits here, holding
 * no connection, and the row locks only ever see one request per talk, speaker or slot from this instance.
 *
 * <p>The row locks stay: they are what keeps the rules when two instances overlap during a deploy. Locks are taken
 * talk first, then speaker, then slot, the same order as the row locks, and always before any row lock, so neither
 * can deadlock. Keys share a fixed set of locks per kind (striping), so unrelated keys now and then wait for each
 * other; that costs a few milliseconds, never correctness.
 */
@Component
public class InstanceLocks {

    private static final int STRIPES = 64;

    private final Stripes talks = new Stripes();
    private final Stripes speakers = new Stripes();
    private final Stripes slots = new Stripes();

    /** Runs {@code work}, typically a transaction, with no other request of this instance working on the talk. */
    public <T> T onTalk(long talkId, Supplier<T> work) {
        return talks.with(Long.hashCode(talkId), work);
    }

    /** Runs {@code work} with no other request of this instance changing the speaker's load. */
    public <T> T onSpeaker(long speakerId, Supplier<T> work) {
        return speakers.with(Long.hashCode(speakerId), work);
    }

    /** Runs {@code work} with no other request of this instance working on the talk or changing the speaker's load. */
    public <T> T onTalkAndSpeaker(long talkId, long speakerId, Supplier<T> work) {
        return onTalk(talkId, () -> onSpeaker(speakerId, work));
    }

    /** Runs {@code work} with no other request of this instance scheduling into the slot, named as in the path. */
    public <T> T onSlot(String slotName, Supplier<T> work) {
        return slots.with(slotName.hashCode(), work);
    }

    /** A fixed set of locks that keys are spread over by their hash. */
    private static final class Stripes {

        private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

        Stripes() {
            for (int i = 0; i < STRIPES; i++) {
                locks[i] = new ReentrantLock();
            }
        }

        <T> T with(int hash, Supplier<T> work) {
            ReentrantLock lock = locks[Math.floorMod(hash, STRIPES)];
            lock.lock();
            try {
                return work.get();
            } finally {
                lock.unlock();
            }
        }
    }
}
