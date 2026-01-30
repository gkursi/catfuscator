package xyz.qweru.cat.transform

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.util.config.ConfigValue
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer

abstract class Transformer(val name: String, val description: String, protected val target: JarContainer, protected val opts: Configuration) {
    protected open fun canTarget(entry: Map.Entry<String, ClassNode>) = entry.value.access and Opcodes.ACC_INTERFACE != Opcodes.ACC_INTERFACE
    protected fun <T> value(name: String, description: String, default: T) = ConfigValue(this, name, description, default)
}