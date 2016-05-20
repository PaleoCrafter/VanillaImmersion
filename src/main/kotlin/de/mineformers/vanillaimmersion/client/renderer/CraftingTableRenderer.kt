package de.mineformers.vanillaimmersion.client.renderer

import de.mineformers.vanillaimmersion.tileentity.CraftingTableLogic
import de.mineformers.vanillaimmersion.tileentity.CraftingTableLogic.Companion.Slot
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIXED
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.item.ItemBlock

/**
 * Renders the items on top of a crafting table.
 */
class CraftingTableRenderer : TileEntitySpecialRenderer<CraftingTableLogic>() {
    override fun renderTileEntityAt(te: CraftingTableLogic, x: Double, y: Double, z: Double,
                                    partialTicks: Float, destroyStage: Int) {
        pushMatrix()
        color(1f, 1f, 1f, 1f)

        // Use the brightness of the block directly above the table for lighting.
        val light = te.world.getCombinedLight(te.pos.add(0, 1, 0), 0)
        val bX = light % 65536
        val bY = light / 65536
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, bX.toFloat(), bY.toFloat())

        // Translate to the table's center and rotate according to its orientation
        translate(x + 0.5, y + 1.0, z + 0.5)
        rotate(180f - te.facing.horizontalAngle, 0f, 1f, 0f)
        // The slots aren't completely centered due to the output slot, translate 1 "pixel" to the left therefore
        translate(0.0625, 0.0, 0.0)

        Minecraft.getMinecraft().textureManager.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
        enableRescaleNormal()
        RenderHelper.enableStandardItemLighting()

        // Render each slot of the table individually
        // TODO: Move this to a custom IBakedModel
        renderItem(te, Slot.IN_TOP_LEFT, 0.1875, 0.1875)
        renderItem(te, Slot.IN_TOP, 0.0, 0.1875)
        renderItem(te, Slot.IN_TOP_RIGHT, -0.1875, 0.1875)

        renderItem(te, Slot.IN_LEFT, 0.1875, 0.0)
        renderItem(te, Slot.IN_MIDDLE, 0.0, 0.0)
        renderItem(te, Slot.IN_RIGHT, -0.1875, 0.0)

        renderItem(te, Slot.IN_BOTTOM_LEFT, 0.1875, -0.1875)
        renderItem(te, Slot.IN_BOTTOM, 0.0, -0.1875)
        renderItem(te, Slot.IN_BOTTOM_RIGHT, -0.1875, -0.1875)

        renderItem(te, Slot.OUTPUT, -0.1875 * 2, 0.0)

        RenderHelper.disableStandardItemLighting()
        disableRescaleNormal()

        popMatrix()
    }

    /**
     * Renders an item on top of the crafting table.
     */
    private fun renderItem(te: CraftingTableLogic, slot: Slot, x: Double, z: Double) {
        pushMatrix()
        // Magic numbers, but this appears to be the perfect offset
        translate(x, 0.0703125, z)
        val stack = te[slot]
        // Most blocks use a block model which requires special treatment
        if (stack?.item is ItemBlock) {
            scale(2f, 2f, 2f)
        }
        // Again, magic numbers
        scale(0.140625, 0.140625, 0.140625)
        Minecraft.getMinecraft().renderItem.renderItem(stack, FIXED)
        popMatrix()
    }
}