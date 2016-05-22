package de.mineformers.vanillaimmersion.integration.jei

import de.mineformers.vanillaimmersion.client.gui.CraftingTableGui
import de.mineformers.vanillaimmersion.tileentity.CraftingTableLogic
import mezz.jei.Internal
import mezz.jei.api.*
import mezz.jei.api.gui.IAdvancedGuiHandler
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.recipe.VanillaRecipeCategoryUid
import mezz.jei.api.recipe.transfer.IRecipeTransferError
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler
import mezz.jei.gui.RecipeLayout
import mezz.jei.input.IKeyable
import mezz.jei.transfer.RecipeTransferErrorInternal
import mezz.jei.util.Log
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container
import net.minecraft.inventory.ContainerWorkbench
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.Optional
import java.awt.Rectangle

/**
 * Vanilla Immersion JEI Integration plugin
 */
@JEIPlugin
class JEIIntegration : BlankModPlugin() {
    companion object {
        lateinit var itemListOverlay: IItemListOverlay
            private set
    }

    override fun register(registry: IModRegistry) {
        registry.recipeTransferRegistry.addRecipeTransferHandler(CraftingTransferHandler())
        registry.addAdvancedGuiHandlers(object : IAdvancedGuiHandler<CraftingTableGui> {
            override fun getGuiContainerClass() =
                CraftingTableGui::class.java

            override fun getGuiExtraAreas(guiContainer: CraftingTableGui) =
                listOf(Rectangle(0, 0, 10, 10))
        })
    }

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        itemListOverlay = jeiRuntime.itemListOverlay
    }
}

object JEIProxy {
    fun focusSearch() {
        if (Loader.isModLoaded("JEI"))
            focusSearchImpl()
    }

    @Optional.Method(modid = "JEI")
    private fun focusSearchImpl() {
        val overlay = JEIIntegration.itemListOverlay
        if (overlay is IKeyable)
            overlay.setKeyboardFocus(true)
    }
}

class CraftingTransferHandler : IRecipeTransferHandler {
    override fun transferRecipe(container: Container, recipeLayout: IRecipeLayout, player: EntityPlayer,
                                maxTransfer: Boolean, doTransfer: Boolean): IRecipeTransferError? {
        val category = (recipeLayout as RecipeLayout).recipeCategory
        val vanillaContainer = ContainerWorkbench(player.inventory, player.worldObj, BlockPos.ORIGIN)
        val transferHandler =
            Internal.getRuntime().recipeRegistry.getRecipeTransferHandler(vanillaContainer, category)
        if (transferHandler == null) {
            if (doTransfer) {
                Log.error("No Recipe Transfer handler for container {}", container.javaClass)
            }
            return RecipeTransferErrorInternal.instance
        }

        val result = transferHandler.transferRecipe(container, recipeLayout, player, maxTransfer, doTransfer)
        if (doTransfer && result == null) {
            player.closeScreen()
//            (player as EntityPlayerSP).connection.sendPacket(CPacketCloseWindow(player.openContainer.windowId))
        }
        return result
    }

    override fun getContainerClass() = CraftingTableLogic.CraftingTableContainer::class.java

    override fun getRecipeCategoryUid() = VanillaRecipeCategoryUid.CRAFTING
}