package xyz.qweru.cat.transform.fake

import org.objectweb.asm.Opcodes
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.generate.pickRandom
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random
import kotlin.random.nextInt

class AntiPatternTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("AntiPattern", "Replace certain insns to prevent decompiler pattern matching", target, opts) {

    val swap by value("Swap", "Replace swap insns", true)
    val const by value("Constant", "Replace constant insns", true)

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

                            if (const) {
                                pass.replace({ it.opcode == Opcodes.ICONST_M1 }) { _, _, _ ->
                                    instructionsFor(method) {
                                        pickRandom(
                                            {
                                                val a = Random.nextLong()
                                                val b = Random.nextLong(Long.MIN_VALUE, a)
                                                ldc(b)
                                                ldc(a)
                                                compareLongs()
                                            },
                                            {
                                                val a = Random.nextDouble()
                                                val b = Random.nextDouble(Double.MIN_VALUE, a)
                                                ldc(b)
                                                ldc(a)
                                                compareDoubles()
                                            },
                                            {
                                                val a = Random.nextFloat()
                                                val b = Random.nextFloat() * (a - Float.MIN_VALUE)
                                                ldc(b)
                                                ldc(a)
                                                compareFloats()
                                            }
                                        )
                                    }
                                }

                                pass.replace({ it.opcode == Opcodes.ICONST_1 }) { _, _, _ ->
                                    instructionsFor(method) {
                                        pickRandom(
                                            {
                                                val a = Random.nextLong()
                                                val b = Random.nextLong(Long.MIN_VALUE, a)
                                                ldc(a)
                                                ldc(b)
                                                compareLongs()
                                            },
                                            {
                                                val a = Random.nextDouble()
                                                val b = Random.nextDouble(Double.MIN_VALUE, a)
                                                ldc(a)
                                                ldc(b)
                                                compareDoubles()
                                            },
                                            {
                                                val a = Random.nextFloat()
                                                val b = Random.nextFloat() * (a - Float.MIN_VALUE)
                                                ldc(a)
                                                ldc(b)
                                                compareFloats()
                                            }
                                        )
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