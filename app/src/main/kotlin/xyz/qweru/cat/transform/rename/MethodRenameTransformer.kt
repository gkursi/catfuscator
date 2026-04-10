package xyz.qweru.cat.transform.rename

import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.isEnum
import xyz.qweru.cat.util.asm.isStatic
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.collections.iterator

class MethodRenameTransformer : Transformer("MethodRename", "Rename methods") {
    private val prefix by value("Prefix", "Prefix for renamed methods", "method")
    private val excludeMain by value("Exclude Main", "Exclude any methods named `main` (required when used as runnable jar)", true)

    override fun apply(target: JarContainer, opts: Configuration) {
        target.apply {
            val parallel = createExecutorFrom(opts)
            for (entry in classes) {
                if (!canTarget(entry)) continue
                parallel {
                    val lookup = mappings.getOrCreateLookup(entry.key).methods
                    val node = entry.value
                    var i = 0L
                    for (method in node.methods) {
                        val name = method.name
                        if (name == "<clinit>" || name == "<init>") continue
                        if (name == "values" && method.isStatic && node.isEnum) continue
                        if (excludeMain && name == "main") continue
                        lookup.put(name, "$prefix$i")
                        i++
                    }
                }
            }

            parallel.await()
        }
    }
}