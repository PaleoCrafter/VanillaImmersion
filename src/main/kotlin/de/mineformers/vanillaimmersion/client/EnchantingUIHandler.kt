package de.mineformers.vanillaimmersion.client

import de.mineformers.vanillaimmersion.VanillaImmersion
import de.mineformers.vanillaimmersion.network.EnchantingAction
import de.mineformers.vanillaimmersion.tileentity.EnchantingTableLogic
import de.mineformers.vanillaimmersion.util.*
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.EnumHand
import net.minecraft.util.Timer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.ReflectionHelper
import javax.vecmath.Matrix4f
import javax.vecmath.Vector3f
import javax.vecmath.Vector4f

/**
 * Handles interaction with the enchantment table's book "GUI".
 */
object EnchantingUIHandler {
    /**
     * Holds a reference to the Minecraft timer, required since we need access to partial ticks in places where
     * you usually don't have it.
     */
    private val TIMER_FIELD by lazy {
        ReflectionHelper.findField(Minecraft::class.java, "field_71428_T", "timer")
    }

    init {
        TIMER_FIELD.isAccessible = true
    }

    /**
     * Property holding the current partial ticks.
     */
    val partialTicks: Float
        get() = (TIMER_FIELD.get(Minecraft.getMinecraft()) as Timer).renderPartialTicks

    /**
     * Handles interaction with enchantment tables when a right click with an empty hand happens.
     */
    @SubscribeEvent
    fun onRightClickEmpty(event: PlayerInteractEvent.RightClickEmpty) {
        onInteract(event, Minecraft.getMinecraft().objectMouseOver?.hitVec ?: Vec3d.ZERO)
    }

    /**
     * Handles interaction with enchantment tables when a right click on a block happens.
     */
    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        onInteract(event, event.hitVec)
    }

    /**
     * Handles interaction with any enchantment tables near a player.
     * Will scan a 7x7x7 cube around the player to check against since we can't rely
     * on the currently hovered block being the enchantment table.
     */
    private fun onInteract(event: PlayerInteractEvent, hovered: Vec3d) {
        val player = event.entityPlayer
        val surroundingBlocks = BlockPos.getAllInBox(player.position - BlockPos(3, 3, 3),
                                                     player.position + BlockPos(3, 3, 3))
        // Filter all enchantment tables out of the surrounding blocks and sort them by distance to the player
        // The sorting is required because otherwise an enchantment table further away might be prioritized.
        val enchantingTables =
            surroundingBlocks
                .filter { player.worldObj.getTileEntity(it) is EnchantingTableLogic }
                .sortedBy { player.getDistanceSq(it) }
                .map { player.worldObj.getTileEntity(it) as EnchantingTableLogic }
        if (enchantingTables.isEmpty())
            return

        val partialTicks =
            if (event.world.isRemote)
                this.partialTicks
            else
                1f
        val origin = player.getPositionEyes(partialTicks)
        val direction = player.getLook(partialTicks)
        val hoverDistance = origin.squareDistanceTo(hovered)
        for (te in enchantingTables) {
            // If the active page is smaller than 0 (normally -1), the book is closed.
            if (te.page < 0)
                continue
            // Try to hit the left page first, then the right one
            if (tryHit(te, player, origin, direction, hoverDistance, partialTicks, false)) {
                if (event.isCancelable)
                    event.isCanceled = true
                return
            }
            if (tryHit(te, player, origin, direction, hoverDistance, partialTicks, true)) {
                if (event.isCancelable)
                    event.isCanceled = true
                return
            }
        }
    }

    /**
     * Sends a ray through the specified enchantment table's left or right page.
     * Returns the hit vector (if the intersection was successful) and a matrix to transform the point
     * to one local to the page.
     */
    private fun intersectPage(te: EnchantingTableLogic,
                              origin: Vec3d, direction: Vec3d,
                              partialTicks: Float, right: Boolean): Pair<Vec3d?, Matrix4f> {
        // Based on ModelBook's flippingPageRight/flippingPageLeft size
        val quad = listOf(Vector4f(0f, 0f, 0f, 1f), Vector4f(6f * 0.0625f, 0f, 0f, 1f),
                          Vector4f(6f * 0.0625f, 8f * 0.0625f, 0f, 1f), Vector4f(0f, 8f * 0.0625f, 0f, 1f))
        val matrix = calculateMatrix(te, partialTicks, right)
        // Transform the quad according to the TE's current status
        quad.forEach { matrix.transform(it) }
        // Translate the local space coordinates to world space
        val page = quad.map { Vec3d(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) + te.pos }
        // Perform a Möller-Trumbore intersection for both triangles the quad can be split into
        val hit = moellerTrumbore(origin, direction, page[0], page[1], page[2]) ?:
                  moellerTrumbore(origin, direction, page[0], page[2], page[3])
        // Invert the transformation matrix to retrieve local coordinates from the hit again
        matrix.invert()
        return Pair(hit, matrix)
    }

    /**
     * Sends a ray through the specified enchantment table's left or right page.
     * Will invoke the desired action for the appropriate page if the ray intersected it.
     */
    private fun tryHit(te: EnchantingTableLogic,
                       player: EntityPlayer, origin: Vec3d, direction: Vec3d, hoverDistance: Double,
                       partialTicks: Float, right: Boolean): Boolean {
        val (hit, matrix) = intersectPage(te, origin, direction, partialTicks, right)
        if (hit != null && origin.squareDistanceTo(hit) < hoverDistance) {
            val localHit = hit - te.pos
            val transformed = Vector4f(localHit.x.toFloat(), localHit.y.toFloat(), localHit.z.toFloat(), 1f)
            matrix.transform(transformed)

            // The "GUI" pixel position of the hit depends on the clicked page since we have two different
            // reference corners
            val pixelHit =
                if (right)
                    Vec3d(0.0, 125.0, 0.0) - Vec3d(-transformed.x.toDouble(), transformed.y.toDouble(), 0.0) / 0.004
                else
                    Vec3d(94.0, 125.0, 0.0) - Vec3d(transformed.x.toDouble(), transformed.y.toDouble(), 0.0) / 0.004
            if (player.worldObj.isRemote) {
                handleUIHit(te, right, player, pixelHit.x, pixelHit.y)
                player.swingArm(EnumHand.MAIN_HAND)
            }
            return true
        }
        return false
    }

    /**
     * Handles a click on the enchantment table's "GUI", performing the appropriate action.
     */
    private fun handleUIHit(table: EnchantingTableLogic, right: Boolean, player: EntityPlayer, x: Double, y: Double) {
        // Just send it off to the server, the client does not have any control
        VanillaImmersion.NETWORK.sendToServer(EnchantingAction.PageHitMessage(table.pos, right, x, y))
    }

    /**
     * Calculates the transformation matrix for the left or right page of an enchantment table.
     * The inverse can be multiplied with any point in the table's local coordinate space to get its position relative
     * to the respective page.
     */
    private fun calculateMatrix(te: EnchantingTableLogic, partialTicks: Float, right: Boolean): Matrix4f {
        // See the enchantment table's renderer, we need to reproduce the transformations exactly
        val hover = te.tickCount + partialTicks
        var dYaw: Float = te.bookRotation - te.bookRotationPrev
        while (dYaw >= Math.PI) {
            dYaw -= Math.PI.toFloat() * 2f
        }

        while (dYaw < -Math.PI) {
            dYaw += Math.PI.toFloat() * 2f
        }

        val yaw = te.bookRotationPrev + dYaw * partialTicks
        var flipLeft = te.pageFlipPrev + (te.pageFlip - te.pageFlipPrev) * partialTicks + 0.25f
        flipLeft = (flipLeft - MathHelper.truncateDoubleToInt(flipLeft.toDouble()).toFloat()) * 1.6f - 0.3f
        if (flipLeft < 0.0f) {
            flipLeft = 0.0f
        }
        if (flipLeft > 1.0f) {
            flipLeft = 1.0f
        }

        val spread = te.bookSpreadPrev + (te.bookSpread - te.bookSpreadPrev) * partialTicks
        val breath = (MathHelper.sin(hover * 0.02f) * 0.1f + 1.25f) * spread
        val rotation = breath - breath * 2.0f * flipLeft

        // Again, see the renderer, the transformations are applied in the same order
        val result = Matrix4f()
        result.setIdentity()
        val tmp = Matrix4f()
        tmp.set(Vector3f(0.5f, 0.75f + 1.6f * 0.0625f + 0.0001f + MathHelper.sin(hover * 0.1f) * 0.01f, 0.5f))
        result.mul(tmp)
        tmp.rotY(-yaw)
        result.mul(tmp)
        tmp.rotZ(80 * Math.PI.toFloat() / 180)
        result.mul(tmp)
        tmp.set(Vector3f(MathHelper.sin(breath) / 16, 0f, 0f))
        result.mul(tmp)
        tmp.rotY(if (right) rotation else -rotation)
        result.mul(tmp)
        tmp.set(Vector3f(0f, -0.25f, 0f))
        result.mul(tmp)

        return result
    }

    /**
     * Performs a Möller-Trumbore intersection on a triangle and returns the position on the vector that was hit,
     * or `null` if there is no intersection.
     * Ignores the winding of the triangle, i.e. does not support backface culling.
     */
    private fun moellerTrumbore(origin: Vec3d, dir: Vec3d, v1: Vec3d, v2: Vec3d, v3: Vec3d,
                                epsilon: Double = 1e-6): Vec3d? {
        // Edges with v1 adjacent to them
        val e1 = v2 - v1
        val e2 = v3 - v1
        // Required for determinant and calculation of u
        val p = dir.crossProduct(e2)
        val det = e1.dotProduct(p)
        // Make sure determinant isn't near zero, otherwise we lie in the triangle's plane
        if (det > -epsilon && det < epsilon) {
            return null
        }
        // Distance from v1 to origin
        val t = origin - v1
        // Calculate u parameter and check whether it's in the triangle's bounds
        val u = t.dotProduct(p) / det
        if (u < 0 || u > 1) {
            return null
        }
        // Calculate v parameter and check whether it's in the triangle's bounds
        val q = t.crossProduct(e1)
        val v = dir.dotProduct(q) / det
        if (v < 0 || u + v > 1) {
            return null
        }
        // Actual intersection test
        val d = e2.dotProduct(q) / det
        if (d > epsilon) {
            // u and v are barycentric coordinates on the triangle, convert them to "normal" ones
            return v1 + u * e1 + v * e2
        }
        return null
    }
}