package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.SciConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * Reserved control capacity: a dedicated control queue with a bounded number
 * of slots (default 8 per sci-config-v1), separate from submission admission.
 * Cancel/reconcile acquire here so saturated submission never starves them;
 * a full control queue rejects explicitly without recording a cancellation.
 */
@Component
public final class ControlAdmission {
    private final Semaphore permits;

    public ControlAdmission() {
        this(SciConfig.defaults().controlCapacity());
    }

    public ControlAdmission(int controlCapacity) {
        if (controlCapacity < 1) throw new IllegalArgumentException("control capacity must be positive");
        this.permits = new Semaphore(controlCapacity);
    }

    /** Non-blocking acquire; false means the control queue is full — reject explicitly. */
    public boolean tryAcquireControl() {
        return permits.tryAcquire();
    }

    public void releaseControl() {
        permits.release();
    }

    public int availableControlPermits() {
        return permits.availablePermits();
    }
}
