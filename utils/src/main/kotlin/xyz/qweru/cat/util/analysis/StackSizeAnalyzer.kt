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
 * todo wip
 *
 * @param verify basic stack height verification
 */
class StackSizeAnalyzer(val verify: Boolean = false) {
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
}