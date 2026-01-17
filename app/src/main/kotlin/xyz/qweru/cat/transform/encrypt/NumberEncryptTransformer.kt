package xyz.qweru.cat.transform.encrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.InsnBuilder
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

private val logger = KotlinLogging.logger { }

class NumberEncryptTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("NumberEncrypt", "Encrypts numeric constants", target, opts, ) {
    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) continue
                parallel {
                    for (method in entry.value.methods) {
                        transformMethod(method) {
                            replace({ it is LdcInsnNode }) { ldc, _, _ ->
                                ldc as LdcInsnNode
                                instructionsFor(method) {
                                    transformConstant(ldc.cst)
                                }
                            }

                            replace({ it is IntInsnNode && it.opcode != Opcodes.NEWARRAY }) { insn, _, _ ->
                                insn as IntInsnNode
                                instructionsFor(method) {
                                    transformConstant(insn.operand)
                                    logger.warn { "XORING " }
                                }
                            }
                        }
                    }
                }
            }
        }

        parallel.await()
    }

    private fun InsnBuilder.transformConstant(value: Any) {
        val choice = Random.nextInt(3)
        when (value) {
            is Integer -> when (choice) {
                0 -> {
                    val c = value.toInt()
                    val b = Random.nextInt() or c
                    val a = c or b.inv()
                    ldc(a)
                    ldc(b)
                    andInts()
                }
                1 -> {
                    val c = value.toInt()
                    val b = Random.nextInt()
                    val a = b xor c
                    ldc(a)
                    ldc(b)
                    xorInts()
                }
                2 -> {
                    val c = value.toInt()
                    val b = Random.nextInt() and c
                    val a = c and b.inv()
                    ldc(a)
                    ldc(b)
                    orInts()
                }
            }

            is Long -> when (choice) {
                0 -> {
                    val b = Random.nextLong() or value
                    val a = value or b.inv()
                    ldc(a)
                    ldc(b)
                    andLongs()
                }
                1 -> {
                    val b = Random.nextLong()
                    val a = b xor value
                    ldc(a)
                    ldc(b)
                    xorLongs()
                }
                2 -> {
                    val b = Random.nextLong() and value
                    val a = value and b.inv()
                    ldc(a)
                    ldc(b)
                    orLongs()
                }
            }

            // todo: double, float
            else -> ldc(value)
        }
    }
}