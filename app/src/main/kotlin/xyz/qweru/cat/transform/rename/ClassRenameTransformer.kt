package xyz.qweru.cat.transform.rename

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.thread.createExecutorFrom

private val logger = KotlinLogging.logger {}

class ClassRenameTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("ClassRename", "Rename classes", target, opts) {
    private val prefix by value("Prefix", "Renamed class prefix","goaway")
    private val preservePackage by value("Keep Package", "Keep original package of class", false)
    private val unicodeCrasher by value("Unicode Crasher", "Appends the null character to crash decompilers", true)

    init {
        target.apply {
            val parallel = createExecutorFrom(opts)
            var i = 0L
            for (entry in classes) {
                if (!canTarget(entry)) continue
                val id = i

                parallel {
                    val builder = StringBuilder()

                    if (preservePackage) {
                        builder.append(getPackage(entry.key))
                    }

                    builder.append(prefix).append("$id")

                    if (unicodeCrasher) {
                        builder.append("\u0000\uBBAA")
                    }

                    val name = builder.toString()
                    mappings.put(entry.key, name)
                }

                i++
            }

            parallel.await()
        }
    }

    private fun getPackage(name: String): String {
        val index = name.lastIndexOf("/")
        return if (index == -1 || index == name.length - 1) ""
               else name.take(index + 1)
    }
}