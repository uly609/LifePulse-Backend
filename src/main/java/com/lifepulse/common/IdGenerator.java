package com.lifepulse.common;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class IdGenerator {
    private final AtomicLong sequence = new AtomicLong();

    public long nextId() {
        long millis = Instant.now().toEpochMilli();
        return millis * 1000 + sequence.getAndIncrement() % 1000;
    }
}
