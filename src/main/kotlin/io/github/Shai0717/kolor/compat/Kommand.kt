package io.github.Shai0717.kolor.compat

import io.github.Shai0717.kolor.KolorCommand
import io.github.Shai0717.kolor.KolorContext
import io.github.Shai0717.kolor.kolorCommand
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class KommandBuilder internal constructor(private val plugin: JavaPlugin) {
    fun register(name: String, vararg aliases: String, block: KommandNode.() -> Unit) {
        plugin.kolorCommand(name, *aliases) { KommandNode(this).block() }
    }
}

class KommandNode internal constructor(private val command: KolorCommand) {
    fun string(): KommandArgument<String> = io.github.Shai0717.kolor.compat.string()
    fun word(): KommandArgument<String> = io.github.Shai0717.kolor.compat.word()
    fun greedyString(): KommandArgument<String> = io.github.Shai0717.kolor.compat.greedyString()
    fun int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): KommandArgument<Int> =
        io.github.Shai0717.kolor.compat.int(min, max)
    fun long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): KommandArgument<Long> =
        io.github.Shai0717.kolor.compat.long(min, max)
    fun float(min: Float = -Float.MAX_VALUE, max: Float = Float.MAX_VALUE): KommandArgument<Float> =
        io.github.Shai0717.kolor.compat.float(min, max)
    fun double(min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): KommandArgument<Double> =
        io.github.Shai0717.kolor.compat.double(min, max)
    fun boolean(): KommandArgument<Boolean> = io.github.Shai0717.kolor.compat.boolean()
    fun player(): KommandArgument<Player> = io.github.Shai0717.kolor.compat.player()
    fun <T : Any> dynamic(parse: (KolorContext, String) -> T): KommandArgument<T> =
        io.github.Shai0717.kolor.compat.dynamic(parse)

    fun requires(predicate: (CommandSender) -> Boolean) = command.requires(predicate)
    fun executes(action: (KolorContext) -> Unit) = command.executes { action(it); 1 }
    fun executesOnEntity(action: (Player, KolorContext) -> Unit) = command.executesOnEntity(action)

    fun then(name: String, block: KommandNode.() -> Unit) =
        command.literal(name) { KommandNode(this).block() }
    fun then(vararg arguments: Pair<String, KommandArgument<*>>, block: KommandNode.() -> Unit) {
        require(arguments.isNotEmpty()) { "At least one argument is required" }
        fun append(node: KommandNode, index: Int) {
            val (name, argument) = arguments[index]
            argument.register(node.command, name) {
                if (index == arguments.lastIndex) block() else append(this, index + 1)
            }
        }
        append(this, 0)
    }
    operator fun String.invoke(block: KommandNode.() -> Unit) = then(this, block)
    operator fun String.invoke(vararg arguments: Pair<String, KommandArgument<*>>, block: KommandNode.() -> Unit) =
        then(this) { then(*arguments, block = block) }
    fun intArg(name: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, block: KommandNode.() -> Unit) =
        command.intArg(name, min, max) { KommandNode(this).block() }
    fun doubleArg(name: String, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE, block: KommandNode.() -> Unit) =
        command.doubleArg(name, min, max) { KommandNode(this).block() }
    fun stringArg(name: String, block: KommandNode.() -> Unit) =
        command.stringArg(name) { KommandNode(this).block() }
    fun greedyStringArg(name: String, block: KommandNode.() -> Unit) =
        command.greedyStringArg(name) { KommandNode(this).block() }
    fun playerArg(name: String, block: KommandNode.() -> Unit) =
        command.playerArg(name) { KommandNode(this).block() }
    fun suggests(action: (KolorContext, com.mojang.brigadier.suggestion.SuggestionsBuilder) -> Unit) =
        command.suggests(action)
}

class KommandArgument<T : Any> internal constructor(
    private val registerNode: (KolorCommand, String, KommandNode.() -> Unit) -> Unit
) {
    internal fun register(command: KolorCommand, name: String, block: KommandNode.() -> Unit) =
        registerNode(command, name, block)
}

private fun <A : Any, T : Any> argument(
    type: ArgumentType<A>,
    rawClass: Class<A>,
    resolve: (KolorContext, A) -> T
): KommandArgument<T> = KommandArgument { command, name, block ->
    command.argument(name, type, rawClass, resolve) { KommandNode(this).block() }
}

fun string(): KommandArgument<String> = argument(StringArgumentType.string(), String::class.java) { _, value -> value }
fun word(): KommandArgument<String> = argument(StringArgumentType.word(), String::class.java) { _, value -> value }
fun greedyString(): KommandArgument<String> =
    argument(StringArgumentType.greedyString(), String::class.java) { _, value -> value }
fun int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): KommandArgument<Int> =
    argument(IntegerArgumentType.integer(min, max), Int::class.javaObjectType) { _, value -> value }
fun long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): KommandArgument<Long> =
    argument(LongArgumentType.longArg(min, max), Long::class.javaObjectType) { _, value -> value }
fun float(min: Float = -Float.MAX_VALUE, max: Float = Float.MAX_VALUE): KommandArgument<Float> =
    argument(FloatArgumentType.floatArg(min, max), Float::class.javaObjectType) { _, value -> value }
fun double(min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): KommandArgument<Double> =
    argument(DoubleArgumentType.doubleArg(min, max), Double::class.javaObjectType) { _, value -> value }
fun boolean(): KommandArgument<Boolean> =
    argument(BoolArgumentType.bool(), Boolean::class.javaObjectType) { _, value -> value }
fun player(): KommandArgument<Player> =
    argument(ArgumentTypes.player(), PlayerSelectorArgumentResolver::class.java) { context, resolver ->
        resolver.resolve(context.brigadier.source).single()
    }

private val invalidDynamicArgument = DynamicCommandExceptionType { message ->
    com.mojang.brigadier.LiteralMessage(message.toString())
}

fun <T : Any> dynamic(parse: (KolorContext, String) -> T): KommandArgument<T> =
    argument(StringArgumentType.word(), String::class.java) { context, value ->
        try {
            parse(context, value)
        } catch (exception: IllegalArgumentException) {
            throw invalidDynamicArgument.create(exception.message ?: "Invalid argument")
        }
    }

fun JavaPlugin.kommand(block: KommandBuilder.() -> Unit) {
    KommandBuilder(this).block()
}
