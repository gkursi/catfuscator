package xyz.qweru.cat.transform.fake

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer

private val logger = KotlinLogging.logger {}

class FakeClassTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("FakeClass", "Create fake classes", target, opts) {
    private val count by value("Count", "Amount of fake classes to create", 100)
    private val prefix by value("Prefix", "Prefix to use for the classes", "FakeClass")
    private val superClasses by value("Superclasses", "Possible superclasses for the fake classes",
        listOf(
            "java.lang.Object",
            "java.lang.Thread",
            "java.net.DatagramSocket",
            "java.io.EOFException"
        )
    )

    init {
        target.apply {
            if (superClasses.isEmpty()) {
                logger.warn { "Setting `Superclasses` is empty, disabling transformer" }
                return@apply
            }

            val version = findCFVersion()

            for (i in 0..<count) {
                val klass = ClassNode()
                val superCl = superClasses.random().replace('.', '/')
                klass.visit(
                    version,
                    Opcodes.ACC_PUBLIC,
                    "$prefix$i",
                    "L$superCl;",
                    superCl,
                    arrayOf()
                )
                target.put(klass)
            }
        }
    }

    private fun findCFVersion(): Int =
        target.classes.entries.first().value.version
}