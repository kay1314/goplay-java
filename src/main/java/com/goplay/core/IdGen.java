package com.goplay.core;

/**
 * IdGen generates sequential IDs that wrap around at max value.
 */
public class IdGen {
    private int id;
    private int max;

    public IdGen(int max) {
        this.id = 0;
        this.max = max;
    }

    public int next() {
        if (++id > max) {
            id = 0;
        }
        return id;
    }

    public void reset() {
        id = 0;
    }

    public int getCurrentId() {
        return id;
    }

    public int getMax() {
        return max;
    }
}
