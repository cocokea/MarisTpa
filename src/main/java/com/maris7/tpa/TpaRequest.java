package com.maris7.tpa;

import java.util.UUID;

public final class TpaRequest {
    public enum Type { TPA, TPAHERE }
    public final UUID requester;
    public final UUID target;
    public final Type type;
    public final long createdAt;

    public TpaRequest(UUID requester, UUID target, Type type, long createdAt) {
        this.requester = requester;
        this.target = target;
        this.type = type;
        this.createdAt = createdAt;
    }

    public boolean expired(long maxMillis) {
        return System.currentTimeMillis() - createdAt > maxMillis;
    }
}
