package com.huntcore.backend;

public interface BackendSyncSink {

    BackendSyncSink NO_OP = new BackendSyncSink() {
        @Override
        public void requestHeartbeat() {
        }

        @Override
        public void publishCompletedMatch(CompletedMatchSnapshot snapshot) {
        }
    };

    void requestHeartbeat();

    void publishCompletedMatch(CompletedMatchSnapshot snapshot);
}
