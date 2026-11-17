package com.github.gamedev.visualenhancements;

/** Relógio em tempo real (não depende dos ticks do jogo). */
public final class Timing {
    private Timing() {
    }

    public static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
