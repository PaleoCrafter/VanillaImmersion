package de.mineformers.vanillaimmersion.config.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraftforge.fml.client.IModGuiFactory

/**
 * Factory for Vanilla Immersion's configuration interface.
 */
class GuiFactory : IModGuiFactory {
    override fun initialize(minecraftInstance: Minecraft?) = Unit

    override fun runtimeGuiCategories() = null

    override fun hasConfigGui() = true

    override fun createConfigGui(parentScreen: GuiScreen) = ConfigGui(parentScreen)
}