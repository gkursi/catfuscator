package xyz.qweru.cat.transform.rename

import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.collections.iterator

class LocalFieldRenameTransformer : Transformer("LocalFieldRename", "Rename local fields") {
    private val prefix by value("Prefix", "Prefix for renamed fields", "field")

    override fun apply(target: JarContainer, opts: Configuration) {
        target.apply {
            val parallel = createExecutorFrom(opts)
            for (entry in classes) {
                if (!canTarget(entry)) continue
                parallel {
                    val lookup = mappings.getOrCreateLookup(entry.key).methods
                    val node = entry.value
                    for (method in node.methods) {
                        val lookup = lookup.getOrCreateLookup(method.name)
                        var i = 0
                        for (localVariable in method.localVariables ?: continue) {
                            lookup.put(localVariable.name, "$prefix$i")
                            i++
                        }
                    }
                }
            }

            parallel.await()
        }
    }
}