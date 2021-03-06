package de.mineformers.vanillaimmersion.client

import de.mineformers.vanillaimmersion.VanillaImmersion
import de.mineformers.vanillaimmersion.client.renderer.Shaders
import de.mineformers.vanillaimmersion.immersion.CraftingHandler
import de.mineformers.vanillaimmersion.immersion.CraftingHandler.splitDrag
import de.mineformers.vanillaimmersion.network.CraftingDrag
import de.mineformers.vanillaimmersion.network.OpenGui
import de.mineformers.vanillaimmersion.tileentity.CraftingTableLogic
import de.mineformers.vanillaimmersion.util.isDown
import de.mineformers.vanillaimmersion.util.minus
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager.color
import net.minecraft.client.renderer.GlStateManager.disableBlend
import net.minecraft.client.renderer.GlStateManager.disableRescaleNormal
import net.minecraft.client.renderer.GlStateManager.enableBlend
import net.minecraft.client.renderer.GlStateManager.enableRescaleNormal
import net.minecraft.client.renderer.GlStateManager.popMatrix
import net.minecraft.client.renderer.GlStateManager.pushMatrix
import net.minecraft.client.renderer.GlStateManager.rotate
import net.minecraft.client.renderer.GlStateManager.scale
import net.minecraft.client.renderer.GlStateManager.translate
import net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11

/**
 * Handles dragging item stacks across a crafting table.
 * Mimics Vanilla GUI mechanics.
 */
object CraftingDragHandler {
    /**
     * The position currently hovered on the crafting grid.
     */
    private var dragPosition: Pair<Int, Int>? = null
    /**
     * The target of the current drag operation, i.e. the position of the crafting table.
     */
    private var dragTarget: BlockPos? = null
    /**
     * Determines whether dragging is currently in progress.
     */
    private var dragging = false
    /**
     * The stack to be distributed across the crafting grid.
     */
    private var dragStack: ItemStack = ItemStack.EMPTY
    /**
     * A list of slots covered by the current dragging process.
     */
    private val dragSlots = mutableListOf<Int>()
    /**
     * A dictionary for looking up the amount each slot will hold after the dragging.
     */
    private val dragAmounts = mutableMapOf<Int, Int>()

    /**
     * Prevents Vanilla from handling right clicks if they happen to slip through.
     */
    fun onStartDragging() {
        if (dragging)
            return
        updateDragTarget()
        // If we have a valid drag target and weren't dragging before: Start dragging
        if (dragTarget != null && dragPosition != null) {
            val (x, y) = dragPosition!!
            if (x in 0..7 && y in 0..7) {
                startDragging()
            }
        }
    }

    @SubscribeEvent
    fun onRightClick(event: PlayerInteractEvent.RightClickItem) {
        if (dragging)
            event.isCanceled = true
    }

    @SubscribeEvent
    fun onRightClick(event: PlayerInteractEvent.RightClickBlock) {
        if (dragging)
            event.isCanceled = true
    }

    /**
     * Continuously updates the dragging process, (re)distributing stacks across the crafting table.
     */
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START)
            return
        if (Minecraft.getMinecraft().world == null || // Covers things like the main menu
            Minecraft.getMinecraft().currentScreen != null) { // We do not want this to happen when a GUI is open
            stopDragging(null)
            return
        }
        val key = Minecraft.getMinecraft().gameSettings.keyBindUseItem
        val keyDown = key.isDown
        val wasDragging = dragging
        val target = dragTarget
        updateDragTarget()
        val heldItem = Minecraft.getMinecraft().player.getHeldItem(EnumHand.MAIN_HAND)
        if (wasDragging && (dragTarget == null || !keyDown || dragStack != heldItem)) {
            stopDragging(target)
        }
        if (dragging && dragTarget != null && dragPosition != null && keyDown) {
            onDrag(dragPosition!!.first, dragPosition!!.second)
        }
    }

    /**
     * Initiates the dragging process.
     */
    fun startDragging() {
        dragging = true
        dragStack = Minecraft.getMinecraft().player.getHeldItem(EnumHand.MAIN_HAND)
        dragSlots.clear()
        dragAmounts.clear()
    }

    /**
     * Updates the list of slots affected by dragging.
     */
    fun onDrag(x: Int, y: Int) {
        // Calculate stack index: 3x3 2D coordinates to 1D index
        val slot = x + y * 3
        if (!dragSlots.contains(slot)) {
            dragSlots.add(x + y * 3)
            dragAmounts.clear()
            val player = Minecraft.getMinecraft().player
            val table = player.world.getTileEntity(dragTarget) as CraftingTableLogic
            for (s in dragSlots) {
                dragAmounts[s] = splitDrag(table, player, dragStack, dragSlots, s)
            }
        }
    }

    /**
     * Resets all variables used by the drag handling.
     * Will notify the server of the dragging if it was successful.
     */
    fun stopDragging(pos: BlockPos?) {
        if (pos != null && dragTarget == pos) {
            val tile = Minecraft.getMinecraft().world.getTileEntity(pos)
            if (tile is CraftingTableLogic && !dragStack.isEmpty) {
                VanillaImmersion.NETWORK.sendToServer(CraftingDrag.Message(pos, dragSlots))
            }
        }
        dragSlots.clear()
        dragAmounts.clear()
        dragging = false
        dragTarget = null
        dragPosition = null
        dragStack = ItemStack.EMPTY
    }

    /**
     * Analyses the block under the user's cursor and checks if its viable for dragging.
     */
    fun updateDragTarget() {
        val world = Minecraft.getMinecraft().world
        val hovered = Minecraft.getMinecraft().objectMouseOver
        // We're only interested in blocks, although a crafting table entity might be interesting
        if (hovered != null && hovered.typeOfHit == RayTraceResult.Type.BLOCK) {
            val tile = world.getTileEntity(hovered.blockPos) as? CraftingTableLogic
            if (tile != null) {
                if (dragTarget == null) {
                    // If the crafting process was just initiated, the block is definitely valid
                    dragTarget = hovered.blockPos
                } else if (dragTarget != hovered.blockPos) {
                    // The cursor has moved onto another block, renders the crafting process invalid
                    dragTarget = null
                    return
                }
                // Dragging may only work on the top face of the crafting table
                if (hovered.sideHit == EnumFacing.UP) {
                    val (x, y) = CraftingHandler.getLocalPos(tile, hovered.hitVec - dragTarget!!)
                    if (x !in 0..7 || y !in 0..7) {
                        dragPosition = null
                        return
                    }
                    val (slotX, modX) = Pair(x / 3, x % 3)
                    val (slotY, modY) = Pair(y / 3, y % 3)
                    // Don't allow the 1 pixel gap between the individual crafting slots to be clicked
                    if (modX == 2 || modY == 2) {
                        dragPosition = null
                        return
                    }
                    dragPosition = slotX.to(slotY)
                    return
                }
            }
        }
        dragTarget = null
    }

    /**
     * Renders a preview of the crafting slots including their changed stack size.
     */
    @SubscribeEvent
    fun onRenderDragStacks(event: RenderWorldLastEvent) {
        if (!dragging || dragSlots.isEmpty()) {
            return
        }
        val te = Minecraft.getMinecraft().world.getTileEntity(dragTarget) as? CraftingTableLogic ?: return

        // The camera is the origin when this event is called, get its position to translate to the world origin
        val camera = Minecraft.getMinecraft().renderViewEntity ?: return
        val cX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks
        val cY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks
        val cZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks

        pushMatrix()
        translate(-cX, -cY, -cZ)
        translate(dragTarget!!.x + 0.5, dragTarget!!.y.toDouble() + 0.875, dragTarget!!.z + 0.5)
        rotate(180f - te.facing.horizontalAngle, 0f, 1f, 0f)
        // Translate the slots 1 "pixel" to the left (when looking at it), since they are not perfectly centered
        translate(0.0625, 0.0, 0.0)

        color(1f, 1f, 1f, 1f)

        // Set up the lighting to take the brightness the block receives
        val light = te.world.getCombinedLight(te.pos.add(0, 1, 0), 0)
        val bX = light % 65536
        val bY = light / 65536
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, bX.toFloat(), bY.toFloat())
        enableStandardItemLighting()

        enableRescaleNormal()

        enableBlend()
        tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)

        Minecraft.getMinecraft().textureManager.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
        // The alpha shader will make every rendered fragment semi-transparent to the given degree
        // Required since normal tinting may be overridden
        Shaders.ALPHA.activate()
        Shaders.ALPHA.setUniformFloat("alpha", 0.5f)
        // Render the preview for each slot
        for (slot in dragSlots) {
            val (x, y) = Pair(slot % 3 - 1, slot / 3 - 1)
            if (dragAmounts.getOrPut(slot, { -1 }) > -1 && te.inventory.getStackInSlot(slot + 1).isEmpty) {
                renderDragStack(x * -0.1875, y * -0.1875)
            }
        }
        Shaders.ALPHA.deactivate()

        disableBlend()
        // Reset the blend function to the default Vanilla uses, otherwise glitches like a "glowing" hand may occur
        tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)

        // Render the amount each slot will contain after dragging
        for (slot in dragSlots) {
            val (x, y) = Pair(slot % 3 - 1, slot / 3 - 1)
            val existing = te.inventory.getStackInSlot(slot + 1).count
            val amount = dragAmounts.getOrPut(slot, { -1 })
            if (amount > -1) {
                pushMatrix()
                translate(x * -0.1875, 0.2, y * -0.1875)
                scale(0.00625f, -0.00625f, 0.00625f)
                rotate(180f, 0f, 1f, 0f)
                val font = Minecraft.getMinecraft().fontRenderer
                // If there's already something in the slot, display a sum
                val s = "${if (existing > 0) existing.toString() + "+" else ""}$amount"
                font.drawString(s, -font.getStringWidth(s) / 2, -font.FONT_HEIGHT, 0xFFFFFF)
                popMatrix()
            }
        }

        disableRescaleNormal()
        RenderHelper.disableStandardItemLighting()

        // Reset colors to not interfere with other renderers
        color(1f, 1f, 1f, 1f)
        popMatrix()
    }

    /**
     * Renders the dragged item stack onto the specified slot.
     */
    private fun renderDragStack(x: Double, z: Double) {
        pushMatrix()
        // Magic numbers, but this appears to be the perfect offset
        translate(x, 0.015, z)
        val stack = dragStack
        // Most blocks use a block model which requires special treatment
        if (stack.item is ItemBlock) {
            translate(0.0, 0.06328125, 0.0)
            scale(2f, 2f, 2f)
        } else {
            // Rotate items to lie down flat on the anvil
            rotate(90f, 1f, 0f, 0f)
            rotate(180f, 0f, 1f, 0f)
        }
        // Some magic numbers, makes items fit perfectly on the crafting grid
        scale(0.140625, 0.140625, 0.140625)
        Minecraft.getMinecraft().renderItem.renderItem(stack, ItemCameraTransforms.TransformType.FIXED)
        popMatrix()
    }

    fun openRecipeGui() {
        if (!dragging) {
            VanillaImmersion.NETWORK.sendToServer(OpenGui.Message(dragTarget!!, 0))
        }
    }
}