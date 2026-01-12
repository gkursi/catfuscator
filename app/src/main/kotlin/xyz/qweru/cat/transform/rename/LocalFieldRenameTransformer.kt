package xyz.qweru.cat.transform.rename

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import kotlin.collections.iterator

private val logger = KotlinLogging.logger {}

class LocalFieldRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("LocalFieldRename", "Rename local fields", target, opts) {
    private val prefix by value("Prefix", "Prefix for renamed fields", "\u0000")

    init {
        target.apply {
            var i = 0L;
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val lookup = mappings.getOrCreateLookup(entry.key).methods
                val node = entry.value
                for (method in node.methods) {
                    val lookup = lookup.getOrCreateLookup(method.name)
                    for (localVariable in method.localVariables ?: continue) {
                        logger.info { "local field : ${entry.key}#${method.name}..${localVariable.name} -> $prefix$i " }
                        lookup.put(localVariable.name, "$prefix$i")
                        i++
                    }
                }
            }
        }
    }
}