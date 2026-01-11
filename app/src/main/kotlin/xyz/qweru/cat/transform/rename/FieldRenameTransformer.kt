package xyz.qweru.cat.transform.rename

import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import kotlin.collections.iterator

class FieldRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("MethodRename", target, opts) {
    private val prefix by value("Prefix", "\u1DE4:3__ Protected by catfuscator :3__")

    init {
        target.apply {
            var i = Long.MIN_VALUE;
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val lookup = mappings.getOrCreateLookup(entry.key).fields
                val node = entry.value
                for (field in node.fields) {
                    println("${entry.key}.${field.name} -> $prefix$i")
                    lookup.put(field.name, "$prefix$i")
                    i++
                }
            }
        }
    }
}