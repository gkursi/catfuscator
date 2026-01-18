package xyz.qweru.cat.transform.encrypt

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

/**
 * idea:
 * generate array for op numbers where every next number is previous +/-/^/&/| some number
 */
class NumberEncryptTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("NumberEncrypt", "Encrypts numeric constants", target, opts, ) {
    val smallConstants by value("Small Constants", "Encrypt small constants (ICONST_*/LCONST_*)", true)

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
                                }
                            }

                            if (!smallConstants) return@transformMethod

                            replace({ isIConst(it.opcode) }) { insn, _, _ ->
                                instructionsFor(method) {
                                    transformConstant(getIConstValue(insn.opcode))
                                }
                            }

                            replace({ isLConst(it.opcode) }) { insn, _, _ ->
                                instructionsFor(method) {
                                    transformConstant(getLConstValue(insn.opcode))
                                }
                            }
                        }
                    }
                }
            }
        }

        parallel.await()
    }

    private fun isIConst(op: Int) =
        op == Opcodes.ICONST_M1
                || op == Opcodes.ICONST_0
                || op == Opcodes.ICONST_1
                || op == Opcodes.ICONST_2
                || op == Opcodes.ICONST_3
                || op == Opcodes.ICONST_4
                || op == Opcodes.ICONST_5

    private fun isLConst(op: Int) =
        op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1

    private fun getIConstValue(op: Int) = when(op) {
        Opcodes.ICONST_M1 -> -1
        Opcodes.ICONST_0 -> 0
        Opcodes.ICONST_1 -> 1
        Opcodes.ICONST_2 -> 2
        Opcodes.ICONST_3 -> 3
        Opcodes.ICONST_4 -> 4
        Opcodes.ICONST_5 -> 5
        else -> throw IllegalArgumentException()
    }

    private fun getLConstValue(op: Int) =
        if (op == Opcodes.LCONST_0) 0L else 1L

    private fun InsnBuilder.transformConstant(value: Any) {
        val choice = Random.nextInt(3)
        when (value) {
            is Int -> when (choice) {
                0 -> {
                    val b = Random.nextInt() or value
                    val a = value or b.inv()
                    ldc(a)
                    ldc(b)
                    andInts()
                }
                1 -> {
                    val b = Random.nextInt()
                    val a = b xor value
                    ldc(a)
                    ldc(b)
                    xorInts()
                }
                2 -> {
                    val b = Random.nextInt() and value
                    val a = value and b.inv()
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