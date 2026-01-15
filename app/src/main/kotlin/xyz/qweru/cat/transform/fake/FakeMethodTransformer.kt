package xyz.qweru.cat.transform.fake

import org.objectweb.asm.Opcodes
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.createExecutorFrom
import xyz.qweru.cat.util.transformClass

class FakeMethodTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("FakeMethod", "Generates fake methods", target, opts) {
    private val prefix by value("Prefix", "Method prefix", "fakeMethod")
    private val count by value("Count", "Amount of fake methods to create", 10)

    private val access by value("Access", "Method access", arrayListOf(Opcodes.ACC_PUBLIC, Opcodes.ACC_PRIVATE))
    private val returnType by value("Return Type", "Method return type (non-void parameters could allow deobfuscators to automatically detect invalid code patterns)", arrayListOf("V"))

    init {
        target.apply {
            val parallel = createExecutorFrom(opts)
            for (entry in classes) {
                if (!canTarget(entry)) continue
                parallel {
                    val node = entry.value
                    transformClass(node) {
                        for (n in 0..<count) {
                            method("$prefix$n", access.random(), "()${returnType.random()}") {
                                returnVoid()
                            }
                        }
                    }
                }
            }

            parallel.await()
        }
    }
}