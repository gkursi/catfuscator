package xyz.qweru.cat.transform.fake

import org.objectweb.asm.Opcodes
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom

class AntiPatternTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("AntiPattern", "Replace certain insns to prevent decompiler pattern matching", target, opts) {

    val swap by value("Swap", "Replace swap insns", true)

    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) return@apply
                val klass = entry.value
                for (method in klass.methods) {
                    parallel {
                        transformMethod(method) {
                            val pass = createPass()

                            if (swap) {
                                pass.replace({ it.opcode == Opcodes.SWAP }) { insn, _, _ ->
                                    instructionsFor(method) {
                                        dup_x1()
                                        pop()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}