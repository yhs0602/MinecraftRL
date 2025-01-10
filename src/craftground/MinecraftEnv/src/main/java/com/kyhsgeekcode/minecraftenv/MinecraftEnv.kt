@file:OptIn(ExperimentalPathApi::class)

package com.kyhsgeekcode.minecraftenv

import com.google.protobuf.ByteString
import com.kyhsgeekcode.minecraftenv.proto.ActionSpace.ActionSpaceMessageV2
import com.kyhsgeekcode.minecraftenv.proto.InitialEnvironment
import com.kyhsgeekcode.minecraftenv.proto.ObservationSpace
import com.kyhsgeekcode.minecraftenv.proto.biomeInfo
import com.kyhsgeekcode.minecraftenv.proto.blockInfo
import com.kyhsgeekcode.minecraftenv.proto.chatMessageInfo
import com.kyhsgeekcode.minecraftenv.proto.entitiesWithinDistance
import com.kyhsgeekcode.minecraftenv.proto.heightInfo
import com.kyhsgeekcode.minecraftenv.proto.hitResult
import com.kyhsgeekcode.minecraftenv.proto.nearbyBiome
import com.kyhsgeekcode.minecraftenv.proto.observationSpaceMessage
import com.mojang.blaze3d.platform.GlConst
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC
import net.minecraft.client.gui.screen.DeathScreen
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.BackgroundRenderer
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.EntityType
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.FluidTags
import net.minecraft.server.MinecraftServer
import net.minecraft.stat.Stats
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import net.minecraft.util.WorldSavePath
import net.minecraft.util.function.BooleanBiFunction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.World
import net.minecraft.world.biome.source.BiomeCoords
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess

enum class ResetPhase {
    WAIT_PLAYER_DEATH,
    WAIT_PLAYER_RESPAWN,
    WAIT_INIT_ENDS,
    END_RESET,
}

enum class IOPhase {
    BEGINNING,
    GOT_INITIAL_ENVIRONMENT_SHOULD_SEND_OBSERVATION,
    GOT_INITIAL_ENVIRONMENT_SENT_OBSERVATION_SKIP_SEND_OBSERVATION,
    READ_ACTION_SHOULD_SEND_OBSERVATION,
    SENT_OBSERVATION_SHOULD_READ_ACTION,
}

val chatList = mutableListOf<ChatMessageRecord>()

class MinecraftEnv :
    ModInitializer,
    CommandExecutor {
    private lateinit var initialEnvironment: InitialEnvironment.InitialEnvironmentMessage
    private var soundListener: MinecraftSoundListener? = null
    private var entityListener: EntityRenderListenerImpl? = null // tracks the entities rendered in the last tick
    private var resetPhase: ResetPhase = ResetPhase.END_RESET
    private var deathMessageCollector: GetMessagesInterface? = null
    private lateinit var audioCapturer: AudioCapturer

    private val tickSynchronizer = TickSynchronizer()
    private val csvLogger = CsvLogger("java_log.csv", enabled = false, profile = false)
//    private var serverPlayerEntity: ServerPlayerEntity? = null

    private val variableCommandsAfterReset = mutableListOf<String>()
    private var skipSync = false
    private var ioPhase = IOPhase.BEGINNING

    override fun onInitialize() {
        val isLdPreloadSet = System.getenv("LD_PRELOAD")
        if (isLdPreloadSet != null) {
            println("LD_PRELOAD is set: $isLdPreloadSet")
        } else {
            println("LD_PRELOAD is not set")
        }
        val socket: SocketChannel
        val messageIO: MessageIO
        try {
            val portStr = System.getenv("PORT")
            val port = portStr?.toInt() ?: 8000
            val verbose =
                when (val verboseStr = System.getenv("VERBOSE")) {
                    "1" -> true
                    "0" -> false
                    else -> verboseStr?.toBoolean() ?: false
                }
            doPrintWithTime = verbose
            val socketFilePath = Path.of("/tmp/minecraftrl_$port.sock")
            socketFilePath.toFile().deleteOnExit()
            csvLogger.log("Connecting to $port")
            printWithTime("Connecting to $port")
            Files.deleteIfExists(socketFilePath)
            val serverSocket =
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socketFilePath))
            csvLogger.profileStartPrint("Minecraft_env/onInitialize/Accept")
            socket = serverSocket.accept()
            csvLogger.profileEndPrint("Minecraft_env/onInitialize/Accept")
            messageIO = DomainSocketMessageIO(socket)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        audioCapturer = AudioCapturer()
        if (audioCapturer.enabled) {
            println("Audio capture enabled")
        } else {
            println("Audio capture disabled")
        }

        skipSync = true
        csvLogger.log("Hello Fabric world!")
        csvLogger.profileStartPrint("Minecraft_env/onInitialize/readInitialEnvironment")
        initialEnvironment = messageIO.readInitialEnvironment()
        csvLogger.profileEndPrint("Minecraft_env/onInitialize/readInitialEnvironment")

        ioPhase = IOPhase.GOT_INITIAL_ENVIRONMENT_SHOULD_SEND_OBSERVATION
        resetPhase = ResetPhase.WAIT_INIT_ENDS
        csvLogger.log("Initial environment read; $ioPhase $resetPhase")
        val initializer = EnvironmentInitializer(initialEnvironment, csvLogger)
        ClientTickEvents.START_CLIENT_TICK.register(
            ClientTickEvents.StartTick { client: MinecraftClient ->
                printWithTime("Start Client tick")
                csvLogger.profileStartPrint("Minecraft_env/onInitialize/ClientTick")
                initializer.onClientTick(client)
                if (soundListener == null) soundListener = MinecraftSoundListener(client.soundManager)
                if (entityListener == null) {
                    entityListener =
                        EntityRenderListenerImpl(client.worldRenderer as AddListenerInterface)
                }
                if (deathMessageCollector == null) {
                    deathMessageCollector =
                        client.networkHandler as GetMessagesInterface?
                }
                csvLogger.profileEndPrint("Minecraft_env/onInitialize/ClientTick")
            },
        )
        ClientTickEvents.START_WORLD_TICK.register(
            ClientTickEvents.StartWorldTick { world: ClientWorld ->
                // read input
                printWithTime("Start client World tick")
                csvLogger.log("Start World tick")
                csvLogger.profileStartPrint("Minecraft_env/onInitialize/ClientWorldTick")
                onStartWorldTick(initializer, world, messageIO)
                csvLogger.profileEndPrint("Minecraft_env/onInitialize/ClientWorldTick")
                csvLogger.log("End World tick")
            },
        )
        ClientTickEvents.END_WORLD_TICK.register(
            ClientTickEvents.EndWorldTick { world: ClientWorld ->
                // allow server to start tick
                tickSynchronizer.notifyServerTickStart()
                // wait until server tick ends
//            printWithTime("Wait server world tick ends")
                csvLogger.profileStartPrint("Minecraft_env/onInitialize/EndWorldTick/WaitServerTickEnds")
                if (skipSync) {
                    csvLogger.log("Skip waiting server world tick ends")
                } else {
                    csvLogger.log("Wait server world tick ends")
                    tickSynchronizer.waitForServerTickCompletion()
                }
                csvLogger.profileEndPrint("Minecraft_env/onInitialize/EndWorldTick/WaitServerTickEnds")
                csvLogger.profileStartPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation")
                if (
                    ioPhase == IOPhase.GOT_INITIAL_ENVIRONMENT_SENT_OBSERVATION_SKIP_SEND_OBSERVATION ||
                    ioPhase == IOPhase.SENT_OBSERVATION_SHOULD_READ_ACTION
                ) {
                    // pass
                    csvLogger.log("Skip send observation; $ioPhase")
                } else {
                    csvLogger.log("Real send observation; $ioPhase")
                    sendObservation(messageIO, world)
                }
                csvLogger.profileEndPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation")
            },
        )
        ServerTickEvents.START_SERVER_TICK.register(
            ServerTickEvents.StartTick { server: MinecraftServer ->
                // wait until client tick ends
                printWithTime("Wait client world tick ends")
                csvLogger.profileStartPrint("Minecraft_env/onInitialize/StartServerTick/WaitClientAction")
                if (skipSync) {
                    csvLogger.log("Server tick start; skip waiting client world tick ends")
                    printWithTime("Server tick start; skip waiting client world tick ends")
                } else {
                    csvLogger.log("Real Wait client world tick ends")
                    printWithTime("Real Wait client world tick ends")
                    tickSynchronizer.waitForClientAction()
                }
                csvLogger.profileEndPrint("Minecraft_env/onInitialize/StartServerTick/WaitClientAction")
            },
        )
        ServerTickEvents.END_SERVER_TICK.register(
            ServerTickEvents.EndTick { server: MinecraftServer ->
                // allow client to end tick
                printWithTime("Notify server tick completion")
                csvLogger.log("Notify server tick completion")
                csvLogger.profileStartPrint("Minecraft_env/onInitialize/EndServerTick/NotifyClientSendObservation")
                tickSynchronizer.notifyClientSendObservation()
                csvLogger.profileEndPrint("Minecraft_env/onInitialize/EndServerTick/NotifyClientSendObservation")
            },
        )
    }

    private fun onStartWorldTick(
        initializer: EnvironmentInitializer,
        world: ClientWorld,
        messageIO: MessageIO,
    ) {
        val client = MinecraftClient.getInstance()
        soundListener!!.onTick()
        if (client.isPaused) return
        val player = client.player ?: return
        if (!player.isDead && client.currentScreen is DeathScreen) {
            sendSetScreenNull(client)
        }
        initializer.onWorldTick(client.server, client.inGameHud.chatHud, this, emptyList())

        when (resetPhase) {
            ResetPhase.WAIT_PLAYER_DEATH -> {
                printWithTime("Waiting for player death")
                csvLogger.log("Waiting for player death")
                if (player.isDead) {
                    player.requestRespawn()
                    resetPhase = ResetPhase.WAIT_PLAYER_RESPAWN
                }
                return
            }

            ResetPhase.WAIT_PLAYER_RESPAWN -> {
                printWithTime("Waiting for player respawn")
                csvLogger.log("Waiting for player respawn")
                if (!player.isDead) {
                    initializer.reset(client.inGameHud.chatHud, this, variableCommandsAfterReset)
                    variableCommandsAfterReset.clear()
                    resetPhase = ResetPhase.WAIT_INIT_ENDS
                }
                return
            }

            ResetPhase.WAIT_INIT_ENDS -> {
                printWithTime("Waiting for the initialization ends")
                csvLogger.log("Waiting for the initialization ends")
                if (initializer.initWorldFinished) {
                    sendSetScreenNull(client) // clear death screen
                    resetPhase = ResetPhase.END_RESET
                }
                return
            }

            ResetPhase.END_RESET -> {
                printWithTime("Reset end")
                csvLogger.log("Reset end")
            }
        }
        try {
            csvLogger.log("Will Read action")
            csvLogger.profileStartPrint("Minecraft_env/onInitialize/ClientWorldTick/ReadAction")
            val action = messageIO.readAction()
            csvLogger.profileEndPrint("Minecraft_env/onInitialize/ClientWorldTick/ReadAction")
            ioPhase = IOPhase.READ_ACTION_SHOULD_SEND_OBSERVATION
            csvLogger.log("Read action done; $ioPhase")
            skipSync = false
            val commands = action.commandsList

            if (commands.isNotEmpty()) {
                for (command in commands) {
                    if (handleCommand(command, client, player)) {
                        return
                    }
                }
            }
            if (player.isDead) {
                return
            } else if (client.currentScreen is DeathScreen) {
                sendSetScreenNull(client)
            }
            if (applyAction(action, player, client)) return
        } catch (e: SocketTimeoutException) {
            printWithTime("Timeout")
            csvLogger.log("Timeout")
        } catch (e: IOException) {
            tickSynchronizer.terminate()
            // release lock
            e.printStackTrace()
            exitProcess(-1)
        } catch (e: Exception) {
            tickSynchronizer.terminate()
            e.printStackTrace()
            exitProcess(-2)
        }
    }

    private fun sendSetScreenNull(client: MinecraftClient) {
        client.setScreen(null)
    }

    // Returns: Should ignore action
    private fun handleCommand(
        command: String,
        client: MinecraftClient,
        player: ClientPlayerEntity,
    ): Boolean {
        if (command == "respawn") {
            if (client.currentScreen is DeathScreen && player.isDead) {
                player.requestRespawn()
                sendSetScreenNull(client)
            }
            return true
        } else if (command.startsWith("fastreset")) {
            printWithTime("Fast resetting")
            csvLogger.log("Fast resetting")
            val extraCommand = command.substringAfter("fastreset ").trim()
            if (extraCommand.isNotEmpty()) {
                val commands = extraCommand.split(";")
                printWithTime("Extra commands: $commands")
                variableCommandsAfterReset.addAll(commands)
            }
            resetPhase = ResetPhase.WAIT_PLAYER_DEATH
//            player.kill() //kill player
            runCommand(player, "/kill @p") // kill player
            runCommand(player, "/tp @e[type=!player] ~ -500 ~") // send to void
            return true
        } else if (command.startsWith("random-summon")) {
            printWithTime("Random summon")
            csvLogger.log("Random summon")
            val arguments = command.substringAfter("random-summon ").trim()
            val argumentsList = arguments.split(" ")
            val entityName = argumentsList[0]
            val x = argumentsList[1].toInt()
            val y = argumentsList[2].toInt()
            val z = argumentsList[3].toInt()
            return false
        } else if (command == "exit") {
            printWithTime("Will terminate")
            csvLogger.log("Will terminate")
            tickSynchronizer.terminate()
            // remove the world file
            client.server?.getSavePath(WorldSavePath.ROOT)?.let {
                try {
                    it.deleteRecursively()
                    printWithTime("Successfully deleted the world $it")
                } catch (e: IOException) {
                    printWithTime("Failed to delete the world $it")
                    e.printStackTrace()
                }
            }
            exitProcess(0)
        } else {
            runCommand(player, command)
            printWithTime("Executed command: $command")
            csvLogger.log("Executed command: $command")
            return false
        }
    }

    private fun applyAction(
        actionDict: ActionSpaceMessageV2,
        player: ClientPlayerEntity,
        client: MinecraftClient,
    ): Boolean {
        csvLogger.profileStartPrint("Minecraft_env/onInitialize/ClientWorldTick/ReadAction/ApplyAction")
        if (actionDict.cameraYaw != 0.0f || actionDict.cameraPitch != 0.0f) {
            val dy = actionDict.cameraPitch * 20.0 / 3
            val dx = actionDict.cameraYaw * 20.0 / 3
            MouseInfo.moveMouseBy(dx.toInt(), dy.toInt())
        }

        // Handle key press
        KeyboardInfo.onAction(actionDict)
        val currentScreen = client.currentScreen
        if (currentScreen != null && currentScreen is DeathScreen) {
            // Disable disconnect button
            return false
        }
        MouseInfo.onAction(actionDict)
        csvLogger.profileEndPrint("Minecraft_env/onInitialize/ClientWorldTick/ReadAction/ApplyAction")
        return false
    }

    private fun sendObservation(
        messageIO: MessageIO,
        world: World,
    ) {
        printWithTime("send Observation")
        csvLogger.log("send Observation")
        val client = MinecraftClient.getInstance()
        val player = client.player
        if (player == null) {
            printWithTime("Player is null")
            csvLogger.log("Player is null")
            return
        }
        if (FramebufferCapturer.checkGLEW()) {
            printWithTime("GLEW initialized")
        } else {
            printWithTime("GLEW not initialized")
            throw RuntimeException("GLEW not initialized")
        }
        if (initialEnvironment.screenEncodingMode == FramebufferCapturer.ZEROCOPY) {
            FramebufferCapturer.initializeZeroCopy(
                initialEnvironment.imageSizeX,
                initialEnvironment.imageSizeY,
                client.framebuffer.colorAttachment,
                client.framebuffer.depthAttachment,
                initialEnvironment.pythonPid,
            )
            csvLogger.log("Initialized zerocopy")
        }

//        FramebufferCapturer.checkExtensionJVM()
        // request stats from server
        // TODO: Use server player stats directly instead of client player stats
        csvLogger.profileStartPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Prepare")
        client.networkHandler?.sendPacket(ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.REQUEST_STATS))
        val buffer = client.framebuffer
        try {
            val imageByteString1: ByteString
            val imageByteString2: ByteString
            val oldX = player.pos.x
            val oldY = player.pos.y
            val oldZ = player.pos.z
            val oldPrevX = player.prevX
            val oldPrevY = player.prevY
            val oldPrevZ = player.prevZ
            val pos = Vec3d(oldX, oldY, oldZ)
            if (initialEnvironment.eyeDistance > 0) {
                // translate the player position to the left and right
                val center = player.pos
                // calculate left direction based on yaw
                // go to the left by eyeWidth block
                val eyeWidth = initialEnvironment.eyeDistance
                val left =
                    center.add(
                        eyeWidth * -sin(Math.toRadians(player.yaw.toDouble())),
                        0.0,
                        eyeWidth * cos(Math.toRadians(player.yaw.toDouble())),
                    )
                // calculate right direction based on yaw
                // go to the right by eyeWidth block
                val right =
                    center.add(
                        eyeWidth * sin(Math.toRadians(player.yaw.toDouble())),
                        0.0,
                        eyeWidth * -cos(Math.toRadians(player.yaw.toDouble())),
                    )
                // go to left and render, take screenshot
                player.prevX = left.x
                player.prevY = left.y
                player.prevZ = left.z
//                player.setPos(left.x, left.y, left.z)
                printWithTime("New left position: ${left.x}, ${left.y}, ${left.z} ${player.prevX}, ${player.prevY}, ${player.prevZ}")
                // (client as ClientRenderInvoker).invokeRender(true)
                render(client)
                imageByteString1 =
                    FramebufferCapturer.captureFramebuffer(
                        buffer.colorAttachment,
                        buffer.fbo,
                        buffer.textureWidth,
                        buffer.textureHeight,
                        initialEnvironment.imageSizeX,
                        initialEnvironment.imageSizeY,
                        initialEnvironment.screenEncodingMode,
                        false,
                        MouseInfo.showCursor, // FramebufferCapturer.isExtensionAvailable
                        MouseInfo.mouseX.toInt(),
                        MouseInfo.mouseY.toInt(),
                    )
                player.prevX = right.x
                player.prevY = right.y
                player.prevZ = right.z
//                player.setPos(right.x, right.y, right.z)
                printWithTime("New right position: ${right.x}, ${right.y}, ${right.z} ${player.prevX}, ${player.prevY}, ${player.prevZ}")
//                (client as ClientRenderInvoker).invokeRender(true)
                render(client)
                imageByteString2 =
                    FramebufferCapturer.captureFramebuffer(
                        buffer.colorAttachment,
                        buffer.fbo,
                        buffer.textureWidth,
                        buffer.textureHeight,
                        initialEnvironment.imageSizeX,
                        initialEnvironment.imageSizeY,
                        initialEnvironment.screenEncodingMode,
                        false,
                        MouseInfo.showCursor, // FramebufferCapturer.isExtensionAvailable
                        MouseInfo.mouseX.toInt(),
                        MouseInfo.mouseY.toInt(),
                    )
                // return to the original position
                player.prevX = oldPrevX
                player.prevY = oldPrevY
                player.prevZ = oldPrevZ
//                player.setPos(oldX, oldY, oldZ)
            } else {
                csvLogger.profileStartPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Prepare/SingleEye/Screenshot")
                csvLogger.profileEndPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Prepare/SingleEye/Screenshot")
                csvLogger.profileStartPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Prepare/SingleEye/ByteString")
                val i: Int =
                    (MouseInfo.mouseX * client.window.scaledWidth.toDouble() / client.window.width.toDouble()).toInt()
                val j: Int =
                    (MouseInfo.mouseY * client.window.scaledHeight.toDouble() / client.window.height.toDouble()).toInt()
                imageByteString1 =
                    FramebufferCapturer.captureFramebuffer(
                        buffer.colorAttachment,
                        buffer.fbo,
                        buffer.textureWidth,
                        buffer.textureHeight,
                        initialEnvironment.imageSizeX,
                        initialEnvironment.imageSizeY,
                        initialEnvironment.screenEncodingMode,
                        false,
                        MouseInfo.showCursor, // FramebufferCapturer.isExtensionAvailable
                        MouseInfo.mouseX.toInt(),
                        MouseInfo.mouseY.toInt(),
                    )
                // ByteString.copyFrom(image1ByteArray)
                imageByteString2 = ByteString.EMPTY // ByteString.copyFrom(image1ByteArray)
                csvLogger.profileEndPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Prepare/SingleEye/ByteString")
            }

            csvLogger.profileStartPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Prepare/Message")
            val observationSpaceMessage =
                observationSpaceMessage {
                    image = imageByteString1
                    x = pos.x
                    y = pos.y
                    z = pos.z
                    pitch = player.pitch.toDouble()
                    yaw = player.yaw.toDouble()
                    health = player.health.toDouble()
                    foodLevel = player.hungerManager.foodLevel.toDouble()
                    saturationLevel = player.hungerManager.saturationLevel.toDouble()
                    isDead = player.isDead
                    val allItems =
                        sequenceOf(
                            player.inventory.main,
                            player.inventory.armor,
                            player.inventory.offHand,
                        ).flatten()
                    inventory.addAll(
                        allItems
                            .map {
                                it.toMessage()
                            }.asIterable(),
                    )

                    if (initialEnvironment.requestRaycast) {
                        raycastResult = player.raycast(100.0, 1.0f, false).toMessage(world)
                    } else {
                        // Optimized: dummy hit result
                        raycastResult =
                            hitResult {
                                type = ObservationSpace.HitResult.Type.MISS
                            }
                    }
                    soundSubtitles.addAll(
                        soundListener!!.entries.map {
                            it.toMessage()
                        },
                    )
                    statusEffects.addAll(
                        player.statusEffects.map {
                            it.toMessage()
                        },
                    )
                    for (killStatKey in initialEnvironment.killedStatKeysList) {
                        val key = EntityType.get(killStatKey).get()
                        val stat = player.statHandler.getStat(Stats.KILLED.getOrCreateStat(key))
                        killedStatistics[killStatKey] = stat
                    }
                    for (mineStatKey in initialEnvironment.minedStatKeysList) {
                        val key = Registries.BLOCK.get(Identifier.of("minecraft", mineStatKey))
                        val stat = player.statHandler.getStat(Stats.MINED.getOrCreateStat(key))
                        minedStatistics[mineStatKey] = stat
                    }
                    for (miscStatKey in initialEnvironment.miscStatKeysList) {
                        val key = Registries.CUSTOM_STAT.get(Identifier.of("minecraft", miscStatKey))
                        miscStatistics[miscStatKey] = player.statHandler.getStat(Stats.CUSTOM.getOrCreateStat(key))
                    }
                    entityListener?.run {
                        for (entity in entities) {
                            // notify where entity is, what it is (supervised)
                            visibleEntities.add(entity.toMessage())
                        }
                    }
                    for (distance in initialEnvironment.surroundingEntityDistancesList) {
                        val distanceDouble = distance.toDouble()
                        val entitiesWithinDistanceMessage =
                            entitiesWithinDistance {
                                world
                                    .getOtherEntities(
                                        player,
                                        player.boundingBox.expand(distanceDouble, distanceDouble, distanceDouble),
                                    ).forEach {
                                        entities.add(it.toMessage())
                                    }
                            }
                        surroundingEntities[distance] = entitiesWithinDistanceMessage
                    }
//                    bobberThrown = serverPlayerEntity?.fishHook != null
                    bobberThrown = player.fishHook != null
                    experience = player.totalExperience
                    worldTime = world.time // world tick, monotonic increasing
                    lastDeathMessage = deathMessageCollector?.lastDeathMessage?.firstOrNull() ?: ""
                    image2 = imageByteString2

                    if (initialEnvironment.requiresSurroundingBlocks) {
                        val blocks = mutableListOf<ObservationSpace.BlockInfo>()
                        for (i in (player.blockX - 1)..(player.blockX + 1)) {
                            for (j in player.blockY - 1..player.blockY + 1) {
                                for (k in player.blockZ - 1..player.blockZ + 1) {
                                    val block = world.getBlockState(BlockPos(i, j, k))
                                    blocks.add(
                                        blockInfo {
                                            x = i
                                            y = j
                                            z = k
                                            translationKey = block.block.translationKey
                                        },
                                    )
                                }
                            }
                        }
                        surroundingBlocks.addAll(blocks)
                    }
                    suffocating = player.isInsideWall
                    eyeInBlock = player.checkIfCameraBlocked()
                    for (chat in chatList) {
                        chatMessages.add(
                            chatMessageInfo {
                                message = chat.message
                                addedTime = chat.addedTime.toLong()
                                indicator = ""
                            },
                        )
                    }
                    chatList.clear()

                    // Populate biome info if needed
                    if (initialEnvironment.requiresBiomeInfo) {
                        println("Get world")
                        val serverWorld = client.server!!.overworld
                        println("End Get world")
                        val currentPlayerBiome =
                            serverWorld.getGeneratorStoredBiome(
                                BiomeCoords.fromBlock(player.blockPos.x),
                                BiomeCoords.fromBlock(player.blockPos.y),
                                BiomeCoords.fromBlock(player.blockPos.z),
                            )
                        println("Current player biome: $currentPlayerBiome")
                        val biomeCenterFinder = BiomeCenterFinder(serverWorld)
                        val biomeCenter =
                            biomeCenterFinder.calculateBiomeCenter(
                                player.blockPos,
                                4,
                                currentPlayerBiome,
                            )
                        println("Finish biome center")
                        if (biomeCenter != null) {
                            biomeInfo =
                                biomeInfo {
                                    centerX = biomeCenter.x
                                    centerY = biomeCenter.y
                                    centerZ = biomeCenter.z
                                    biomeName = currentPlayerBiome.idAsString
                                }
                        }
                        val nearbyBiomes1 = biomeCenterFinder.getNearbyBiomes(player.blockPos, 2)
                        for (biomePos in nearbyBiomes1) {
                            nearbyBiomes.add(
                                nearbyBiome {
                                    x = biomePos.x
                                    y = biomePos.y
                                    z = biomePos.z
                                    biomeName = biomePos.biome.idAsString
                                },
                            )
                        }
                        println("Finish nearby biome")
                    }

                    submergedInWater = player.isSubmergedInWater
                    isInLava = player.isInLava
                    submergedInLava = player.isSubmergedIn(FluidTags.LAVA)

                    if (initialEnvironment.requiresHeightmap) {
                        val heightMapProvider = HeightMapProvider()
                        val heightMap = heightMapProvider.getHeightMap(world, player.blockPos, 1)
                        for (heightMapInfo in heightMap) {
                            heightInfo.add(
                                heightInfo {
                                    x = heightMapInfo.x
                                    z = heightMapInfo.z
                                    height = heightMapInfo.height
                                    blockName = heightMapInfo.blockName
                                },
                            )
                        }
                    }
                    isOnGround = player.isOnGround
                    isTouchingWater = player.isTouchingWater
                    if (initialEnvironment.screenEncodingMode == FramebufferCapturer.ZEROCOPY) {
                        ipcHandle = FramebufferCapturer.ipcHandle
                    }
                }
            if (ioPhase == IOPhase.GOT_INITIAL_ENVIRONMENT_SHOULD_SEND_OBSERVATION) {
//                csvLogger.log("Sent observation; $ioPhase")
                ioPhase = IOPhase.GOT_INITIAL_ENVIRONMENT_SENT_OBSERVATION_SKIP_SEND_OBSERVATION
//                csvLogger.log("Sent observation; now $ioPhase")
            } else if (ioPhase == IOPhase.READ_ACTION_SHOULD_SEND_OBSERVATION) {
//                csvLogger.log("Sent observation; $ioPhase")
                ioPhase = IOPhase.SENT_OBSERVATION_SHOULD_READ_ACTION
//                csvLogger.log("Sent observation; now $ioPhase")
            } else {
//                csvLogger.log("Sent observation; $ioPhase good.")
            }
            csvLogger.profileEndPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Prepare/Message")
            csvLogger.profileStartPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Write")
            messageIO.writeObservation(observationSpaceMessage)
            csvLogger.profileEndPrint("Minecraft_env/onInitialize/EndWorldTick/SendObservation/Write")
        } catch (e: IOException) {
            e.printStackTrace()
            tickSynchronizer.terminate()
            client.scheduleStop()

            val threadGroup = Thread.currentThread().threadGroup
            val threads = arrayOfNulls<Thread>(threadGroup.activeCount())
            threadGroup.enumerate(threads)

            for (thread in threads) {
                if (thread == null) {
                    continue
                }
                if (thread != Thread.currentThread()) {
                    thread.interrupt()
                }
            }
            printWithTime("Will exitprocess -3")
            exitProcess(-3)
        }
    }

    override fun runCommand(
        player: ClientPlayerEntity,
        command: String,
    ) {
        var command = command
        printWithTime("Running command: $command")
        csvLogger.log("Running command: $command")
        if (command.startsWith("/")) {
            command = command.substring(1)
        }
        player.networkHandler.sendChatCommand(command)
        printWithTime("End send command: $command")
        csvLogger.log("End send command: $command")
    }
}

fun ClientPlayerEntity.checkIfCameraBlocked(): Boolean {
    val f: Float = EntityType.PLAYER.dimensions.width() * 0.8f
    val box = Box.of(this.eyePos, f.toDouble(), 1.0E-6, f.toDouble())
    return BlockPos.stream(box).anyMatch { pos: BlockPos ->
        val blockState: BlockState = this.world.getBlockState(pos)
        !blockState.isAir &&
            VoxelShapes.matchesAnywhere(
                blockState
                    .getCollisionShape(
                        this.world,
                        pos,
                    ).offset(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()),
                VoxelShapes.cuboid(box),
                BooleanBiFunction.AND,
            )
    }
}

fun render(client: MinecraftClient) {
    RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT or GlConst.GL_COLOR_BUFFER_BIT, IS_SYSTEM_MAC)
    client.framebuffer.beginWrite(true)
    BackgroundRenderer.clearFog()
    RenderSystem.enableCull()
    val l = Util.getMeasuringTimeNano()
    client.gameRenderer.render(
        client.renderTickCounter, // client.renderTickCounter.tickDelta,
        true, // tick
    )
    client.framebuffer.endWrite()
    client.framebuffer.draw(client.window.framebufferWidth, client.window.framebufferHeight)
}
