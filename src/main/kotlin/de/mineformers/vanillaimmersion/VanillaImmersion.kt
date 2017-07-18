package de.mineformers.vanillaimmersion

import de.mineformers.vanillaimmersion.block.*
import de.mineformers.vanillaimmersion.client.BeaconHandler
import de.mineformers.vanillaimmersion.client.CraftingDragHandler
import de.mineformers.vanillaimmersion.client.renderer.*
import de.mineformers.vanillaimmersion.config.Configuration
import de.mineformers.vanillaimmersion.immersion.CraftingHandler
import de.mineformers.vanillaimmersion.immersion.EnchantingHandler
import de.mineformers.vanillaimmersion.item.Hammer
import de.mineformers.vanillaimmersion.network.*
import de.mineformers.vanillaimmersion.tileentity.*
import de.mineformers.vanillaimmersion.util.SubSelectionHandler
import de.mineformers.vanillaimmersion.util.SubSelectionRenderer
import net.minecraft.block.Block
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.item.Item
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundEvent
import net.minecraftforge.client.event.ModelRegistryEvent
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.client.model.obj.OBJLoader
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.fml.relauncher.Side
import org.apache.logging.log4j.LogManager
import net.minecraft.init.Blocks as VBlocks
import net.minecraft.init.Items as VItems

/**
 * Main entry point for Vanilla Immersion
 */
@Mod(modid = VanillaImmersion.MODID,
     name = VanillaImmersion.MOD_NAME,
     version = VanillaImmersion.VERSION,
     acceptedMinecraftVersions = "*",
     dependencies = "required-after:forgelin;required-after:forge",
     updateJSON = "@UPDATE_URL@",
     modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
     guiFactory = "de.mineformers.vanillaimmersion.config.gui.GuiFactory")
object VanillaImmersion {
    const val MOD_NAME = "Vanilla Immersion"
    const val MODID = "vimmersion"
    const val VERSION = "@VERSION@"

    /**
     * Proxy for client- or server-specific code
     */
    @SidedProxy
    lateinit var PROXY: Proxy
    /**
     * SimpleImpl network instance for client-server communication
     */
    val NETWORK by lazy {
        SimpleNetworkWrapper(MODID)
    }
    /**
     * Logger for the mod to inform people about things.
     */
    val LOG by lazy {
        LogManager.getLogger(MODID)
    }

    init {
        MinecraftForge.EVENT_BUS.register(Blocks)
        MinecraftForge.EVENT_BUS.register(Items)
        MinecraftForge.EVENT_BUS.register(Sounds)
    }

    /**
     * Runs during the pre-initialization phase of mod loading, registers blocks, items etc.
     */
    @EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        Configuration.load(event.modConfigurationDirectory, "vimmersion")

        if (!Configuration.shouldKeepVanilla("crafting_table")) {
            MinecraftForge.EVENT_BUS.register(CraftingHandler)
        }
        if (!Configuration.shouldKeepVanilla("enchanting_table")) {
            MinecraftForge.EVENT_BUS.register(EnchantingHandler)
        }
        MinecraftForge.EVENT_BUS.register(SubSelectionHandler)

        // Register messages and handlers
        NETWORK.registerMessage(AnvilLock.Handler, AnvilLock.Message::class.java,
                                0, Side.CLIENT)
        NETWORK.registerMessage(AnvilText.Handler, AnvilText.Message::class.java,
                                1, Side.SERVER)
        NETWORK.registerMessage(CraftingDrag.Handler, CraftingDrag.Message::class.java,
                                2, Side.SERVER)
        NETWORK.registerMessage(OpenGui.Handler, OpenGui.Message::class.java,
                                3, Side.SERVER)
        NETWORK.registerMessage(BeaconScroll.Handler, BeaconScroll.Message::class.java,
                                4, Side.SERVER)
        NetworkRegistry.INSTANCE.registerGuiHandler(this, GuiHandler())

        PROXY.preInit(event)
    }

    /**
     * Holder object for all blocks introduced by this mod.
     * Due to be reworked once MinecraftForge's substitutions are fixed.
     * Blocks utilize lazy initialization to guarantee they're not created before first access in [Blocks.init]
     */
    object Blocks {
        /**
         * Initializes and registers blocks and related data
         */
        @SubscribeEvent
        fun init(event: RegistryEvent.Register<Block>) {
            // TODO: Unify interaction handling?
            if (!Configuration.shouldKeepVanilla("furnace")) {
                LOG.info("Overriding furnace with immersive version!")
                event.registry.register(Furnace(false))
                event.registry.register(Furnace(true))
                registerTileEntity(FurnaceLogic::class.java, "furnace")
            }
            if (!Configuration.shouldKeepVanilla("crafting_table")) {
                LOG.info("Overriding crafting table with immersive version!")
                event.registry.register(CraftingTable())
                registerTileEntity(CraftingTableLogic::class.java, "crafting_table")
            }
            if (!Configuration.shouldKeepVanilla("anvil")) {
                LOG.info("Overriding anvil with immersive version!")
                event.registry.register(Anvil())
                registerTileEntity(AnvilLogic::class.java, "anvil")
            }
            if (!Configuration.shouldKeepVanilla("enchanting_table")) {
                LOG.info("Overriding enchantment table with immersive version!")
                event.registry.register(EnchantingTable())
                registerTileEntity(EnchantingTableLogic::class.java, "enchanting_table")
            }
            if (!Configuration.shouldKeepVanilla("brewing_stand")) {
                LOG.info("Overriding brewing stand with immersive version!")
                event.registry.register(BrewingStand())
                registerTileEntity(BrewingStandLogic::class.java, "brewing_stand")
            }
            if (!Configuration.shouldKeepVanilla("beacon")) {
                LOG.info("Overriding beacon with immersive version!")
                event.registry.register(Beacon())
                registerTileEntity(BeaconLogic::class.java, "beacon")
            }
        }

        @SubscribeEvent
        fun onMissingMappings(event: RegistryEvent.MissingMappings<Block>) {
            for (mapping in event.mappings) {
                mapping.remap(ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", mapping.key.resourcePath)))
            }
        }

        private fun <T : TileEntity> registerTileEntity(clazz: Class<T>, name: String) {
            GameRegistry.registerTileEntity(clazz, "minecraft:$name")
        }
    }

    object Items {
        /**
         * Hammer for interaction with Anvil
         */
        val HAMMER by lazy {
            Hammer()
        }

        /**
         * Initializes and registers blocks and related data
         */
        @SubscribeEvent
        fun init(event: RegistryEvent.Register<Item>) {
            event.registry.register(HAMMER)
        }

        @SubscribeEvent
        fun onMissingMappings(event: RegistryEvent.MissingMappings<Item>) {
            for (mapping in event.mappings) {
                mapping.remap(ForgeRegistries.ITEMS.getValue(ResourceLocation("minecraft", mapping.key.resourcePath)))
            }
        }
    }

    /**
     * Holder object for all sound (events) added by this mod.
     */
    object Sounds {
        /**
         * Page turn sound for enchantment table
         */
        val ENCHANTING_PAGE_TURN = SoundEvent(ResourceLocation("$MODID:enchanting.page_turn"))

        /**
         * Initializes and registers sounds and related data
         */
        @SubscribeEvent
        fun init(event: RegistryEvent.Register<SoundEvent>) {
            event.registry.register(ENCHANTING_PAGE_TURN.setRegistryName(ResourceLocation("$MODID:enchanting.page_turn")))
        }
    }

    /**
     * Interface for client & server proxies
     */
    interface Proxy {
        /**
         * Performs pre-initialization tasks for the proxy's side.
         */
        fun preInit(event: FMLPreInitializationEvent)
    }

    /**
     * The client proxy serves as client-specific interface for the mod.
     * Code that may only be accessed on the client should be put here.
     */
    @Mod.EventBusSubscriber(Side.CLIENT, modid = MODID)
    class ClientProxy : Proxy {
        override fun preInit(event: FMLPreInitializationEvent) {
            OBJLoader.INSTANCE.addDomain(MODID) // We don't have OBJ models yet, but maybe in the future?
            // Initialize (i.e. compile) shaders now, removes delay on initial use later on
            Shaders.init()

            // Register client-side block features
            if (!Configuration.shouldKeepVanilla("furnace")) {
                ClientRegistry.bindTileEntitySpecialRenderer(FurnaceLogic::class.java, FurnaceRenderer())
            }
            if (!Configuration.shouldKeepVanilla("crafting_table")) {
                ClientRegistry.bindTileEntitySpecialRenderer(CraftingTableLogic::class.java, CraftingTableRenderer())
                MinecraftForge.EVENT_BUS.register(CraftingDragHandler)
            }
            if (!Configuration.shouldKeepVanilla("anvil")) {
                ClientRegistry.bindTileEntitySpecialRenderer(AnvilLogic::class.java, AnvilRenderer())
            }
            if (!Configuration.shouldKeepVanilla("enchanting_table")) {
                ClientRegistry.bindTileEntitySpecialRenderer(EnchantingTableLogic::class.java, EnchantingTableRenderer())
            }
            if (!Configuration.shouldKeepVanilla("brewing_stand")) {
                ClientRegistry.bindTileEntitySpecialRenderer(BrewingStandLogic::class.java, BrewingStandRenderer())
            }
            if (!Configuration.shouldKeepVanilla("beacon")) {
                ClientRegistry.bindTileEntitySpecialRenderer(BeaconLogic::class.java, BeaconRenderer())
                MinecraftForge.EVENT_BUS.register(BeaconHandler)
            }

            // Register client-specific event handlers
            MinecraftForge.EVENT_BUS.register(SubSelectionRenderer)
        }

        companion object {
            @JvmStatic
            @SubscribeEvent
            fun registerModels(event: ModelRegistryEvent) {
                ModelLoader.setCustomModelResourceLocation(Items.HAMMER, 0, ModelResourceLocation("$MODID:hammer", "inventory"))
            }
        }
    }

    /**
     * The server proxy serves as server-specific interface for the mod.
     * Code that may only be accessed on the sver should be put here.
     */
    class ServerProxy : Proxy {
        override fun preInit(event: FMLPreInitializationEvent) {
        }
    }
}
