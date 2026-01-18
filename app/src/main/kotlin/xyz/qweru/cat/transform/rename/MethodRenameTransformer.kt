package xyz.qweru.cat.transform.rename

import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.collections.iterator

class MethodRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("MethodRename", "Rename methods", target, opts) {
    private val prefix by value("Prefix", "Prefix for renamed methods", "method")
    private val excludeMain by value("Exclude Main", "Exclude any methods named `main` (required when used as runnable jar)", true)

    init {
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