package de.mineformers.vanillaimmersion.client.gui

import de.mineformers.vanillaimmersion.integration.jei.JEIProxy
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.inventory.Container
import org.lwjgl.input.Keyboard

/**
 * "GUI" for JEI integration.
 */
class CraftingTableGui(container: Container) : GuiContainer(container) {
    private var setFocus = false

    override fun initGui() {
        super.initGui()
        guiLeft = 0
        guiTop = 0
        xSize = width / 2
        ySize = height
    }

    override fun drawGuiContainerBackgroundLayer(partialTicks: Float, mouseX: Int, mouseY: Int) {
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        this.drawDefaultBackground()
        if (!setFocus) {
            JEIProxy.focusSearch()
            setFocus = true
        }
    }

    override fun handleMouseInput() {
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE)
            Minecraft.getMinecraft().thePlayer.closeScreen()
    }
}