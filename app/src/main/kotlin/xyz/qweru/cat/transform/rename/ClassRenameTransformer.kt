package xyz.qweru.cat.transform.rename

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer

private val logger = KotlinLogging.logger {}

class ClassRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("ClassRename", target, opts) {
    private val prefix by value("Prefix", "\u1DE4:3__ Protected by catfuscator :3__")

    init {
        target.apply {
            var i = Long.MIN_VALUE;
            for (entry in classes) {
                if (!canTarget(entry)) continue
                mappings.put(entry.key, "$prefix$i")
                logger.info { "class : ${entry.key} -> $prefix$i" }
                i++
            }
        }
    }
}