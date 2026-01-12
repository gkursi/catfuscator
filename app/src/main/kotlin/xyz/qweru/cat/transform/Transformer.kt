package xyz.qweru.cat.transform

import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.config.ConfigValue
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer

abstract class Transformer(val name: String, val description: String, protected val target: JarContainer, protected val opts: Configuration) {
    protected fun canTarget(entry: Map.Entry<String, ClassNode>) = true
    protected fun <T> value(name: String, description: String, default: T) = ConfigValue(this, name, description, default)
}