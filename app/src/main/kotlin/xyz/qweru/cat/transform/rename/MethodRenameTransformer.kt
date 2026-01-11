package xyz.qweru.cat.transform.rename

import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import kotlin.collections.iterator

class MethodRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("MethodRename", target, opts) {
    private val prefix by value("Prefix", "\u1DE4:3__ Protected by catfuscator :3__")
    private val excludeMain by value("Exclude Main", true)

    init {
        target.apply {
            var i = Long.MIN_VALUE;
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val lookup = mappings.getOrCreateLookup(entry.key).methods
                val node = entry.value
                for (method in node.methods) {
                    val name = method.name
                    if (name == "<clinit>" || name == "<init>") continue
                    if (excludeMain && name == "main") continue
                    println("${entry.key}#${method.name} -> $prefix$i")
                    lookup.put(name, "$prefix$i")
                    i++
                }
            }
        }
    }
}