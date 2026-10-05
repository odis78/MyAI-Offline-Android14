package com.dmitry.myai.infinix.verification;

import java.util.Objects;

public final class VerificationEngine {
    public enum Status { SUCCESS, RETRY, FAILED }

    public static final class Observation {
        private final String before;
        private final String after;
        private final boolean actionAccepted;

        public Observation(String before, String after, boolean actionAccepted) {
            this.before = before == null ? "" : before;
            this.after = after == null ? "" : after;
            this.actionAccepted = actionAccepted;
        }

        public String before() { return before; }
        public String after() { return after; }
        public boolean actionAccepted() { return actionAccepted; }
    }

    public Status verify(Observation observation, boolean expectedScreenChange) {
        if (observation == null || !observation.actionAccepted()) return Status.RETRY;
        if (!expectedScreenChange) return Status.SUCCESS;
        return Objects.equals(observation.before(), observation.after())
                ? Status.RETRY
                : Status.SUCCESS;
    }

    public Status afterRetry(int retryCount) {
        return retryCount >= 2 ? Status.FAILED : Status.RETRY;
    }
}
