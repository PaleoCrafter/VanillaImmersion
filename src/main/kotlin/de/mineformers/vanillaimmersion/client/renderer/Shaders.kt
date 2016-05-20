package de.mineformers.vanillaimmersion.client.renderer

import net.minecraft.util.ResourceLocation

/**
 * Holder object for shaders, allows for sharing and preloading.
 */
object Shaders {
    /**
     * The alpha shader renders anything translucently, adding onto OpenGL's own coloring.
     */
    final val ALPHA by lazy {
        Shader(null, ResourceLocation("vimmersion", "alpha"))
    }
    /**
     * The embers shader slowly turns the rendered object into ashes.
     */
    final val EMBERS by lazy {
        Shader(null, ResourceLocation("vimmersion", "embers"))
    }

    internal fun init() {
        ALPHA.init()
        EMBERS.init()
    }
}