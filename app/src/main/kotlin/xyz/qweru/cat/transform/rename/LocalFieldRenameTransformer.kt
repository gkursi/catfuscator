package xyz.qweru.cat.transform.rename

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.createExecutorFrom
import kotlin.collections.iterator

class LocalFieldRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("LocalFieldRename", "Rename local fields", target, opts) {
    private val prefix by value("Prefix", "Prefix for renamed fields", "hi")

    init {
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