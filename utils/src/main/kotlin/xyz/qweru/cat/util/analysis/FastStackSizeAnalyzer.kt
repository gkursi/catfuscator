package xyz.qweru.cat.util.analysis

import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

/**
 * Fast stack size analysis implementation.
 * Worst case scenario: O(MN) time complexity (M - instruction count, N - fork count).
 * Always has O(2M) space complexity.
 * Longs and doubles are represented with 2 slots.
 *
 * To compare:
 * Any ASM-based analyzer will have at least O(5M) space complexity,
 * and generally takes longer
 *
 * @param verify basic stack height verification
 */
class FastStackSizeAnalyzer(val verify: Boolean = false) {
    private lateinit var array: IntArray

    fun analyze(insns: InsnList) {
        array = IntArray(insns.size()) { -1 }
        fork(insns, insns.first, 0, 0)
    }

    operator fun get(i: Int): Int = array[i]

    private fun fork(insns: InsnList, insnNode: AbstractInsnNode?, i: Int, s: Int) {
        var node = insnNode ?: return
        var verifyOnly = true

        var index = i
        var stack = s

        while (true) {
            if (array[index] != -1) {
                // this path has already been calculated

                if (!verify) {
                    return
                }

                verifyOnly = false // avoid infinite loops
            }

            if (!verifyOnly && array[index] != stack) {
                throw IllegalStateException("Stack height mismatch at index $i")
            } else {
                array[index] = stack
            }

            if (node.isFork || node.isTerminating) {
                break
            }

            index++
            stack += node.stackChange
            node = node.next ?: return
        }

        if (node.isFork && verifyOnly) {
            when (node.opcode) {
                Opcodes.GOTO -> {
                    val label = (node as JumpInsnNode).label

                    fork(insns, label, insns.indexOf(label), stack)
                }

                in Opcodes.IFEQ..Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                    val label = (node as JumpInsnNode).label

                    stack--
                    fork(insns, node.next, ++index, stack)
                    fork(insns, label, insns.indexOf(label), stack)
                }

                in Opcodes.IF_ICMPEQ..Opcodes.IF_ACMPNE -> {
                    val label = (node as JumpInsnNode).label

                    stack -= 2
                    fork(insns, node.next, ++index, stack)
                    fork(insns, label, insns.indexOf(label), stack)
                }

                Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH -> {
                    val labels = when (node) {
                        is TableSwitchInsnNode -> node.labels + node.dflt
                        is LookupSwitchInsnNode -> node.labels + node.dflt

                        else -> throw IllegalStateException("Invalid switch opcode, m")
                    }

                    stack--
                    labels.forEach {
                        fork(insns, it, insns.indexOf(it), stack)
                    }
                }
            }
        }
    }

    private val AbstractInsnNode.stackChange: Int
        get() = when (opcode) {
            Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD -> 1
            Opcodes.DLOAD, Opcodes.LLOAD -> 2

            Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1 -> 2
            Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.ICONST_M1, Opcodes.ICONST_0,
            Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4,
            Opcodes.ICONST_5 -> 1

            Opcodes.IALOAD, Opcodes.FALOAD, Opcodes.AALOAD, Opcodes.BALOAD,
            Opcodes.CALOAD, Opcodes.SALOAD -> -1
            Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.ASTORE -> -1
            Opcodes.LSTORE, Opcodes.DSTORE -> -2
            Opcodes.IASTORE, Opcodes.FASTORE, Opcodes.AASTORE, Opcodes.BASTORE,
            Opcodes.CASTORE, Opcodes.SASTORE -> -3
            Opcodes.LASTORE, Opcodes.DASTORE -> -4

            Opcodes.BIPUSH, Opcodes.SIPUSH -> 1
            Opcodes.POP -> -1
            Opcodes.POP2 -> -2

            Opcodes.DUP, Opcodes.DUP_X1, Opcodes.DUP_X2 -> 1
            Opcodes.DUP2, Opcodes.DUP2_X1, Opcodes.DUP2_X2 -> 2

            Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG -> 0

            Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR -> -1

            Opcodes.LADD, Opcodes.DADD, Opcodes.LSUB, Opcodes.DSUB,
            Opcodes.LMUL, Opcodes.DMUL, Opcodes.LDIV, Opcodes.DDIV,
            Opcodes.LAND, Opcodes.LXOR, Opcodes.LOR, Opcodes.LREM,
            Opcodes.DREM -> -2

            Opcodes.LCMP, Opcodes.DCMPG, Opcodes.DCMPL -> -3
            Opcodes.FCMPG, Opcodes.FCMPL -> -1

            Opcodes.I2D, Opcodes.I2L, Opcodes.F2D, Opcodes.F2L -> 1
            Opcodes.D2I, Opcodes.L2I, Opcodes.D2F, Opcodes.L2F -> -1

            Opcodes.GETSTATIC ->
                if ((this as FieldInsnNode).desc.isDoubleDesc) 2 else 1

            Opcodes.GETFIELD ->
                if ((this as FieldInsnNode).desc.isDoubleDesc) 1 else 0

            Opcodes.PUTSTATIC ->
                if ((this as FieldInsnNode).desc.isDoubleDesc) -2 else -1

            Opcodes.PUTFIELD ->
                if ((this as FieldInsnNode).desc.isDoubleDesc) -3 else -2

            in Opcodes.IADD..Opcodes.LXOR -> -1
            in Opcodes.ACONST_NULL..Opcodes.ICONST_5 -> 1

            in Opcodes.INVOKEVIRTUAL..Opcodes.INVOKEDYNAMIC -> {
                val size = Type.getReturnType(this.dsc).size -
                        Type.getArgumentTypes(this.dsc).sumOf { it.size }

                if (opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.INVOKEDYNAMIC) {
                    size - 1
                } else {
                    size
                }
            }

            Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> -1

            Opcodes.MULTIANEWARRAY -> {
                this as MultiANewArrayInsnNode
                -this.dims + 1
            }

            Opcodes.NEW -> 1

            Opcodes.LDC -> {
                this as LdcInsnNode

                when (this.cst) {
                    is Long, is Double -> 2

                    is Int, is Float,
                    is Type, is Handle,
                    is String, is ConstantDynamic -> 1

                    else -> throw IllegalStateException("invalid constant: $cst")
                }
            }

            else -> 0
        }

    private val String.isDoubleDesc: Boolean
        get() = this == "J" || this == "D"

    private val AbstractInsnNode.isFork: Boolean
        get() = this is JumpInsnNode
                || this is TableSwitchInsnNode
                || this is LookupSwitchInsnNode

    private val AbstractInsnNode.dsc
        get() = when (this) {
            is MethodInsnNode -> desc
            is InvokeDynamicInsnNode -> desc
            else -> throw IllegalArgumentException()
        }

    private val AbstractInsnNode.isTerminating: Boolean
        get() = opcode in Opcodes.IRETURN..Opcodes.RETURN
                || opcode == Opcodes.ATHROW
}