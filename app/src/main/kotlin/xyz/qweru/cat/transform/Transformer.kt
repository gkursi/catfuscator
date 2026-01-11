package xyz.qweru.cat.transform

import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.config.ConfigValue
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer

abstract class Transformer(val name: String, private val target: JarContainer, private val opts: Configuration) {

    protected fun canTarget(entry: Map.Entry<String, ClassNode>) = true

    protected fun <T> value(name: String, default: T) = ConfigValue(this, name, default)
}