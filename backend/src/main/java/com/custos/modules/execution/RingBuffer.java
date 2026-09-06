package com.custos.modules.execution;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe bounded ring buffer to store log lines with bounded memory usage.
 */
public class RingBuffer<T> {

    private final Object[] buffer;
    private final int capacity;
    private int writeIndex = 0;
    private int size = 0;
    private final Object lock = new Object();

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    public void add(T item) {
        synchronized (lock) {
            buffer[writeIndex] = item;
            writeIndex = (writeIndex + 1) % capacity;
            if (size < capacity) {
                size++;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public List<T> toList() {
        synchronized (lock) {
            List<T> list = new ArrayList<>(size);
            if (size < capacity) {
                for (int i = 0; i < size; i++) {
                    list.add((T) buffer[i]);
                }
            } else {
                for (int i = 0; i < capacity; i++) {
                    int index = (writeIndex + i) % capacity;
                    list.add((T) buffer[index]);
                }
            }
            return list;
        }
    }

    public int size() {
        synchronized (lock) {
            return size;
        }
    }

    public void clear() {
        synchronized (lock) {
            for (int i = 0; i < capacity; i++) {
                buffer[i] = null;
            }
            writeIndex = 0;
            size = 0;
        }
    }
}
