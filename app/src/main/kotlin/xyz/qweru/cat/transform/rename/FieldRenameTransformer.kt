package xyz.qweru.cat.transform.rename

import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.collections.iterator

class FieldRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("FieldRename", "Rename fields", target, opts) {
    private val prefix by value("Prefix", "Prefix for renamed fields", "\u0000:3__ Protected by catfuscator :3__")

    init {
        target.apply {
            val parallel = createExecutorFrom(opts)
            for (entry in classes) {
                if (!canTarget(entry)) continue
                parallel {
                    val lookup = mappings.getOrCreateLookup(entry.key).fields
                    val node = entry.value
                    var i = 0
                    for (field in node.fields) {
                        lookup.put(field.name, "$prefix$i")
                        i++
                    }
                }
            }
            parallel.await()
        }
    }
}