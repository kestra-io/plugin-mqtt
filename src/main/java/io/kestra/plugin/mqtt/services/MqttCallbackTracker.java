package io.kestra.plugin.mqtt.services;

final class MqttCallbackTracker {
    private final Object monitor = new Object();
    private boolean accepting = true;
    private int inFlight;

    boolean enter() {
        synchronized (monitor) {
            if (!accepting) {
                return false;
            }

            inFlight++;
            return true;
        }
    }

    void exit() {
        synchronized (monitor) {
            inFlight--;
            if (inFlight == 0) {
                monitor.notifyAll();
            }
        }
    }

    void stop() {
        synchronized (monitor) {
            accepting = false;
        }
    }

    void await() throws InterruptedException {
        synchronized (monitor) {
            while (inFlight > 0) {
                monitor.wait();
            }
        }
    }

    void stopAndAwait() throws InterruptedException {
        stop();
        await();
    }
}
