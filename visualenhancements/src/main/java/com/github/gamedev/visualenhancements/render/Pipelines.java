package com.github.gamedev.visualenhancements.render;

import com.github.gamedev.visualenhancements.VisualEnhancementsClient;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Pipelines de render próprios (o 1.21.11 não tem mais RenderSystem.setShader).
 * Ambos usam quads com posição + cor.
 */
public final class Pipelines {
    /** Brilho do chão: mistura normal, testa profundidade mas não escreve nela. */
    public static final RenderPipeline WET_GROUND = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(VisualEnhancementsClient.MOD_ID, "pipeline/wet_ground"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    /**
     * Aura de impacto: mistura aditiva (brilho). Testa profundidade, então o corpo
     * da entidade e o mundo ocultam o que está atrás, o que faz a energia parecer
     * realmente envolver o modelo.
     */
    public static final RenderPipeline HIT_AURA = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(VisualEnhancementsClient.MOD_ID, "pipeline/hit_aura"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private Pipelines() {
    }

    /** Força o registro dos pipelines durante a inicialização do mod. */
    public static void init() {
    }
}
