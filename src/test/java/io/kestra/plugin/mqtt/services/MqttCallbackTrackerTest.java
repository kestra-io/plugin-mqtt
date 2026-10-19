package io.kestra.plugin.mqtt.services;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttCallbackTrackerTest {
    @Test
    void waitsForInFlightCallbackBeforeReturning() throws Exception {
        var tracker = new MqttCallbackTracker();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        Thread callback = Thread.ofVirtual().start(() -> {
            if (tracker.enter()) {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    tracker.exit();
                }
            }
        });

        assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
        tracker.stop();

        var finished = new CountDownLatch(1);
        Thread waiting = Thread.ofVirtual().start(() -> {
            try {
                tracker.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        });
        assertFalse(finished.await(100, TimeUnit.MILLISECONDS));

        release.countDown();
        callback.join();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
    }

    @Test
    void rejectsCallbacksAfterStopping() throws Exception {
        var tracker = new MqttCallbackTracker();

        tracker.stopAndAwait();

        assertThat(tracker.enter(), is(false));
    }
}
