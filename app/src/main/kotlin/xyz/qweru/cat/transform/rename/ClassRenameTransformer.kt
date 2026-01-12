package xyz.qweru.cat.transform.rename

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer

private val logger = KotlinLogging.logger {}

class ClassRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("ClassRename", "Rename classes", target, opts) {
    private val prefix by value("Prefix", "Renamed class prefix",":3__ Protected by catfuscator :3__")
    private val preservePackage by value("Keep Package", "Keep original package of class", false)
    private val unicodeCrasher by value("Unicode Crasher", "Appends the null character to crash decompilers", false)
    private val fakeDirectory by value("Fake Directory", "Appends the `/` character to crash some decompilers", false)

    init {
        target.apply {
            var i = Long.MIN_VALUE;
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val builder = StringBuilder()

                if (unicodeCrasher) {
                    builder.append("\u0000")
                }

                if (preservePackage) {
                    builder.append(getPackage(entry.key))
                }

                builder.append(prefix).append("$i")

                if (fakeDirectory) {
                    builder.append("/")
                }

                val name = builder.toString()
                mappings.put(entry.key, name)
                logger.info { "class : ${entry.key} -> $name" }
                i++
            }
        }
    }

    private fun getPackage(name: String): String {
        val index = name.lastIndexOf("/")
        return if (index == -1 || index == name.length - 1) ""
               else name.take(index + 1)
    }
}