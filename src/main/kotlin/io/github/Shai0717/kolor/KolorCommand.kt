package io.github.Shai0717.kolor

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import kotlin.reflect.KProperty

typealias BrigadierContext = CommandContext<CommandSourceStack>

class KolorContext(
    val brigadier: BrigadierContext,
    private val argumentReaders: Map<String, (KolorContext) -> Any> = emptyMap()
) {
    val sender: CommandSender get() = brigadier.source.sender
    fun int(name: String): Int = IntegerArgumentType.getInteger(brigadier, name)
    fun double(name: String): Double = DoubleArgumentType.getDouble(brigadier, name)
    fun string(name: String): String = StringArgumentType.getString(brigadier, name)
    fun player(name: String): Player =
        brigadier.getArgument(name, PlayerSelectorArgumentResolver::class.java).resolve(brigadier.source).single()

    fun argument(name: String): Any =
        argumentReaders[name]?.invoke(this) ?: throw IllegalArgumentException("Unknown compatibility argument: $name")

    inline operator fun <reified T : Any> get(name: String): T =
        T::class.javaObjectType.cast(argument(name))

    inline fun <reified T : Any> getArgument(name: String): T = get(name)
    inline fun <reified T : Any> parseArgument(name: String): T = get(name)

    @Suppress("UNCHECKED_CAST")
    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T = argument(property.name) as T
}

class KolorCommand internal constructor(
    private val plugin: JavaPlugin,
    private val node: ArgumentBuilder<CommandSourceStack, *>,
    private val argumentReaders: Map<String, (KolorContext) -> Any> = emptyMap()
) {
    fun requires(predicate: (CommandSender) -> Boolean) {
        node.requires { predicate(it.sender) }
    }

    fun executes(action: (KolorContext) -> Int) {
        node.executes { action(KolorContext(it, argumentReaders)) }
    }

    fun executesOnEntity(action: (Player, KolorContext) -> Unit) {
        executes { context ->
            val player = context.sender as? Player
            if (player == null) {
                context.sender.sendMessage(Component.text("This command requires a player."))
                0
            } else {
                val task = with(KolorScheduler(plugin)) {
                    player.runOn(retiredCallback = {
                        plugin.logger.warning("Command execution cancelled: player retired")
                    }) { action(player, context) }
                }
                if (task == null) {
                    plugin.logger.warning("Command execution rejected: player already retired")
                    0
                } else 1
            }
        }
    }

    fun suggests(action: (KolorContext, SuggestionsBuilder) -> Unit) {
        val argument = node as? RequiredArgumentBuilder<CommandSourceStack, *>
            ?: error("Suggestions can only be attached to an argument")
        argument.suggests { context, builder ->
            action(KolorContext(context, argumentReaders), builder)
            builder.buildFuture()
        }
    }

    fun literal(name: String, block: KolorCommand.() -> Unit) =
        child(Commands.literal(name), block)

    fun intArg(name: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, block: KolorCommand.() -> Unit) =
        child(Commands.argument(name, IntegerArgumentType.integer(min, max)), block)

    fun doubleArg(name: String, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE, block: KolorCommand.() -> Unit) =
        child(Commands.argument(name, DoubleArgumentType.doubleArg(min, max)), block)

    fun stringArg(name: String, block: KolorCommand.() -> Unit) =
        child(Commands.argument(name, StringArgumentType.word()), block)

    fun greedyStringArg(name: String, block: KolorCommand.() -> Unit) =
        child(Commands.argument(name, StringArgumentType.greedyString()), block)

    fun playerArg(name: String, block: KolorCommand.() -> Unit) =
        child(Commands.argument(name, ArgumentTypes.player()), block)

    fun <T : Any> argument(
        name: String,
        type: ArgumentType<T>,
        valueClass: Class<T>,
        resolve: (KolorContext, T) -> Any,
        block: KolorCommand.() -> Unit
    ) {
        val readers = argumentReaders + (name to { context: KolorContext ->
            resolve(context, context.brigadier.getArgument(name, valueClass))
        })
        child(Commands.argument(name, type), block, readers)
    }

    private fun child(
        child: ArgumentBuilder<CommandSourceStack, *>,
        block: KolorCommand.() -> Unit,
        readers: Map<String, (KolorContext) -> Any> = argumentReaders
    ) {
        KolorCommand(plugin, child, readers).block()
        node.then(child)
    }
}

fun JavaPlugin.kolorCommand(name: String, vararg aliases: String, block: KolorCommand.() -> Unit) {
    require(name.isNotBlank()) { "Command name must not be blank" }
    val root = Commands.literal(name)
    KolorCommand(this, root).block()
    lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
        event.registrar().register(root.build(), aliases.toList())
    }
}
