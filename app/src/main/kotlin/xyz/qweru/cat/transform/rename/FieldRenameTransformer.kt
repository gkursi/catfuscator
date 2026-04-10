package xyz.qweru.cat.transform.rename

import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.collections.iterator

class FieldRenameTransformer : Transformer("FieldRename", "Rename fields") {
    private val prefix by value("Prefix", "Prefix for renamed fields", "field")

    override fun apply(target: JarContainer, opts: Configuration) {
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