package xyz.qweru.cat.transform

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.config.ConfigValue
import xyz.qweru.cat.config.ExcludeConfigValue
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer

private val logger = KotlinLogging.logger {  }

abstract class Transformer(val name: String, val description: String, protected val target: JarContainer, protected val opts: Configuration) {
    private val _exclusions = ExcludeConfigValue(
        this,
        "Filter",
        "Filter transformer targets"
    )

    val exclusions by _exclusions

    init {
        logger.info { "Applying ${this.javaClass.simpleName}" }
    }

    protected fun excludeRegex(string: String) =
        _exclusions.excludeRegex(string)

    protected fun excludeLiteral(string: String) =
        _exclusions.exclude(string)

    protected open fun canTarget(entry: Map.Entry<String, ClassNode>) =
        entry.value.access and Opcodes.ACC_INTERFACE != Opcodes.ACC_INTERFACE
                && !exclusions.any { it.matches(entry.key) }

    protected open fun canTarget(klass: String) =
        !exclusions.any { it.matches(klass) }

    protected fun <T> value(name: String, description: String, default: T) =
        ConfigValue(this, name, description, default)
}