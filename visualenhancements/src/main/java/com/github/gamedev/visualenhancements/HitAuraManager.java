package com.github.gamedev.visualenhancements;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Detecta acertos confirmados e guarda as auras ativas.
 *
 * Um clique de ataque sozinho não conta: o alvo só recebe a aura se, logo em
 * seguida, o próprio jogo mostrar que ele foi ferido (animação de dano) ou
 * morreu. Ataques que erram, são bloqueados ou caem em tempo de invulnerabilidade
 * não geram aura.
 */
public final class HitAuraManager {
    public static final long DURATION_MS = 1700L;
    private static final int MAX_AURAS = 6;
    /** Quantos ticks esperamos o servidor confirmar o dano (12 ticks = 0,6 s). */
    private static final int CONFIRM_WINDOW_TICKS = 12;

    public record Aura(LivingEntity target, long startMs) {
    }

    private static final class Pending {
        final LivingEntity target;
        final int hurtTimeAtAttack;
        final boolean dyingAtAttack;
        int ticksLeft = CONFIRM_WINDOW_TICKS;

        Pending(LivingEntity target) {
            this.target = target;
            this.hurtTimeAtAttack = target.hurtTime;
            this.dyingAtAttack = target.isDeadOrDying();
        }
    }

    private static final List<Aura> AURAS = new ArrayList<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private HitAuraManager() {
    }

    /** Chamado quando o jogador local tenta atacar uma entidade. */
    public static InteractionResult onAttack(Player player, Level world, InteractionHand hand,
                                             Entity entity, EntityHitResult hitResult) {
        if (!world.isClientSide() || !VisualModule.HIT_AURA.isEnabled()) {
            return InteractionResult.PASS;
        }
        if (player != Minecraft.getInstance().player) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof LivingEntity living) || living.isRemoved()) {
            return InteractionResult.PASS;
        }

        for (Pending pending : PENDING) {
            if (pending.target == living) {
                pending.ticksLeft = CONFIRM_WINDOW_TICKS;
                return InteractionResult.PASS;
            }
        }
        PENDING.add(new Pending(living));

        // Só observa: nunca cancela o ataque
        return InteractionResult.PASS;
    }

    /** Chamado a cada tick do cliente: confirma (ou descarta) os ataques pendentes. */
    public static void tick() {
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            LivingEntity target = pending.target;

            if (target.isRemoved()) {
                iterator.remove();
                continue;
            }

            boolean hurt = target.hurtTime > 0 && target.hurtTime > pending.hurtTimeAtAttack;
            boolean killed = !pending.dyingAtAttack && target.isDeadOrDying();
            if (hurt || killed) {
                startAura(target);
                iterator.remove();
            } else if (--pending.ticksLeft <= 0) {
                iterator.remove();
            }
        }
    }

    private static void startAura(LivingEntity target) {
        long now = Timing.nowMs();

        // Acerto em quem já tem aura: reinicia sem passar de novo pelo fade-in
        long start = now;
        Iterator<Aura> iterator = AURAS.iterator();
        while (iterator.hasNext()) {
            Aura existing = iterator.next();
            if (existing.target() == target) {
                start = now - 420L;
                iterator.remove();
            }
        }

        AURAS.add(new Aura(target, start));
        if (AURAS.size() > MAX_AURAS) {
            AURAS.remove(0);
        }
    }

    public static List<Aura> active() {
        return AURAS;
    }

    public static void removeExpired(long nowMs) {
        AURAS.removeIf(aura -> nowMs - aura.startMs() >= DURATION_MS);
    }

    public static void clear() {
        AURAS.clear();
        PENDING.clear();
    }
}
