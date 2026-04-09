package xyz.qweru.cat.util.analysis

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Frame
import xyz.qweru.cat.util.collection.IntStack
import xyz.qweru.cat.util.math.XXH3

/**
 * Fast stack analysis.
 * O(mn + kn) time complexity (m - instruction count, n - fork count, k - max stack size).
 * O(m + kn) space complexity.
 * <p>
 *
 * ToDo: rewrite without recursion
 *
 * @param verify basic stack verification
 */
class FrameStateAnalyzer(val verify: Boolean = false) : Iterable<Long> {
    internal lateinit var array: LongArray
    internal lateinit var height: StackSizeAnalyzer
    internal var cuh: Array<Frame<BasicValue>?>? = null

    internal val map by lazy {
        val map = Long2ObjectOpenHashMap<MutableSet<Int>>()

        for ((index, hash) in array.withIndex()) {
            map.getOrPut(hash) { IntOpenHashSet() }
                .add(index)
        }

        map
    }

    fun analyze(insns: InsnList): FrameStateAnalyzer {
        array = LongArray(insns.size()) { -1 }
        height = StackSizeAnalyzer().apply { analyze(insns);  }

        fork(
            insns,
            insns.first,
            0,
            IntStack(
                initialCapacity = 20,
                factor = 1.1
            )
        )

        return this
    }

    operator fun get(i: Int): Long = array[i]

    override fun iterator(): Iterator<Long> =
        array.iterator()

    private fun fork(insns: InsnList, insnNode: AbstractInsnNode?, i: Int, stack: IntStack) {
        var node = insnNode ?: return
        var noVerify = true
        var index = i

        while (true) {
            if (array[index] != -1L) {
                // this path has already been calculated

                if (!verify) {
                    return
                }

                noVerify = false
            }

            val hash = XXH3.hash64(
                stack,
                IntStack.StackAccess,
                stack.size() * 4L
            )

            if (!noVerify && array[index] != hash) {
                throw IllegalStateException("Stack mismatch at index $index")
            } else {
                array[index] = hash
            }

            if (height[index] != stack.size()) {
                IllegalStateException("Stack height mismatch at $index: ${stack.size()}/${height[index]}/${cuh?.get(index)} (${height[index -1]})"
                        + "\nContext:\n"
                        + "    " + node.previous?.previous?.previous?.javaClass?.simpleName + " \n"
                        + "    " + node.previous?.previous?.javaClass?.simpleName + " \n"
                        + "    " + node.previous?.javaClass?.simpleName + " \n"
                        + "    " + node.javaClass.simpleName + " <<\n"
                        + "    " + node.next?.javaClass?.simpleName + "\n"
                        + "prev: ${node.previous?.opcode}"
                ).apply {
                    printStackTrace(System.out)
                    System.out.flush()
                    throw this
                }
            }

            println("size at $index: ${stack.size()}/${height[index]} pre op ${node.javaClass.simpleName}(${node.opcode})")

            if (node.isFork || node.isTerminating) {
                break
            }

            index++
            node.applyToStack(stack)
            node = node.next ?: return
        }

        if (node.isFork && noVerify) {
            when (node.opcode) {
                Opcodes.GOTO -> {
                    val label = (node as JumpInsnNode).label

                    fork(insns, label, insns.indexOf(label), stack.clone())
                }

                in Opcodes.IFEQ..Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                    val label = (node as JumpInsnNode).label

                    stack.pop()
                    fork(insns, node.next, ++index, stack.clone())
                    fork(insns, label, insns.indexOf(label), stack.clone())
                }

                in Opcodes.IF_ICMPEQ..Opcodes.IF_ACMPNE -> {
                    val label = (node as JumpInsnNode).label

                    stack.pop(2)
                    fork(insns, node.next, ++index, stack.clone())
                    fork(insns, label, insns.indexOf(label), stack.clone())
                }

                Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH -> {
                    val labels = when (node) {
                        is TableSwitchInsnNode -> node.labels + node.dflt
                        is LookupSwitchInsnNode -> node.labels + node.dflt

                        else -> throw IllegalStateException("Invalid switch opcode")
                    }

                    stack.pop()
                    labels.forEach {
                        fork(insns, it, insns.indexOf(it), stack.clone())
                    }
                }
            }
        }
    }

    fun group(): Map<Long, MutableSet<Int>> = map

    private fun AbstractInsnNode.applyToStack(stack: IntStack) {
        when (opcode) {
            Opcodes.LASTORE, Opcodes.DASTORE -> stack.pop(4)
            Opcodes.LSTORE, Opcodes.DSTORE -> stack.pop(2)

            in Opcodes.IALOAD..Opcodes.SALOAD -> stack.pop(2)
            in Opcodes.IASTORE..Opcodes.SASTORE -> stack.pop(3)
            in Opcodes.ISTORE..Opcodes.ASTORE -> stack.pop(1)

            Opcodes.LCMP, Opcodes.DCMPG, Opcodes.DCMPL -> stack.pop(4)
            Opcodes.FCMPG, Opcodes.FCMPL -> stack.pop(2)

            Opcodes.POP -> stack.pop(1)
            Opcodes.POP2 -> stack.pop(2)
        }

        val value = when (opcode) {
            Opcodes.ILOAD -> Value.INT
            Opcodes.FLOAD -> Value.FLOAT
            Opcodes.ALOAD -> Value.REF
            Opcodes.DLOAD -> Value.DOUBLE
            Opcodes.LLOAD -> Value.LONG

            Opcodes.ACONST_NULL -> Value.REF

            Opcodes.LCONST_0, Opcodes.LCONST_1 -> Value.LONG
            Opcodes.DCONST_0, Opcodes.DCONST_1 -> Value.DOUBLE
            Opcodes.FCONST_0, Opcodes.FCONST_1 -> Value.FLOAT

            Opcodes.ICONST_M1, Opcodes.ICONST_0,
            Opcodes.ICONST_1, Opcodes.ICONST_2,
            Opcodes.ICONST_3, Opcodes.ICONST_4,
            Opcodes.ICONST_5 -> Value.INT

            Opcodes.IALOAD, Opcodes.BALOAD,
            Opcodes.CALOAD, Opcodes.SALOAD -> Value.INT
            Opcodes.DALOAD -> Value.DOUBLE
            Opcodes.LALOAD -> Value.LONG
            Opcodes.FALOAD -> Value.FLOAT
            Opcodes.AALOAD -> Value.REF

            Opcodes.BIPUSH, Opcodes.SIPUSH -> Value.INT

            Opcodes.DUP -> stack.push(stack.peek())
            Opcodes.DUP_X1 -> stack.insert(stack.size() - 1, stack.peek())
            Opcodes.DUP_X2 -> stack.insert(stack.size() - 2, stack.peek())

            Opcodes.DUP2 -> {
                val a = stack[stack.size() - 2]
                val b = stack.peek()

                stack.push(a)
                stack.push(b)
            }

            Opcodes.DUP2_X1 -> {
                val a = stack[stack.size() - 2]
                val b = stack.peek()

                stack.insert(stack.size() - 3, b)
                stack.insert(stack.size() - 4, a)
            }

            Opcodes.DUP2_X2 -> {
                val a = stack[stack.size() - 2]
                val b = stack.peek()

                stack.insert(stack.size() - 3, b)
                stack.insert(stack.size() - 4, a)
            }

            Opcodes.LUSHR, Opcodes.LSHR,
            Opcodes.LSHL -> {
                stack.pop(3)
                Value.LONG
            }

            Opcodes.LREM -> {
                stack.pop(3)
                Value.LONG
            }

            Opcodes.DREM -> {
                stack.pop(3)
                Value.DOUBLE
            }

            Opcodes.D2I, Opcodes.L2I -> {
                stack.pop(2)
                Value.INT
            }

            Opcodes.D2F, Opcodes.L2F -> {
                stack.pop(2)
                Value.FLOAT
            }

            Opcodes.I2D, Opcodes.F2D -> {
                stack.pop()
                Value.DOUBLE
            }

            Opcodes.I2L, Opcodes.F2L -> {
                stack.pop()
                Value.LONG
            }

            Opcodes.LADD, Opcodes.LSUB,
            Opcodes.LMUL, Opcodes.LDIV,
            Opcodes.LAND, Opcodes.LOR,
            Opcodes.LXOR -> {
                stack.pop(4)
                Value.LONG
            }

            Opcodes.IADD, Opcodes.ISUB,
            Opcodes.IMUL, Opcodes.IDIV,
            Opcodes.IAND, Opcodes.IOR,
            Opcodes.IXOR, Opcodes.IREM,
            Opcodes.ISHR, Opcodes.ISHL,
            Opcodes.IUSHR -> {
                stack.pop(2)
                Value.INT
            }

            Opcodes.FADD, Opcodes.FSUB,
            Opcodes.FMUL, Opcodes.FDIV,
            Opcodes.FREM -> {
                stack.pop(2)
                Value.FLOAT
            }

            Opcodes.DADD, Opcodes.DSUB,
            Opcodes.DMUL, Opcodes.DDIV -> {
                stack.pop(4)
                Value.DOUBLE
            }

            Opcodes.LCMP, Opcodes.DCMPG,
            Opcodes.FCMPG, Opcodes.FCMPL,
            Opcodes.DCMPL -> Value.INT

            Opcodes.GETSTATIC -> Value.fromDesc((this as FieldInsnNode).desc)

            Opcodes.GETFIELD -> {
                stack.pop()
                Value.fromDesc((this as FieldInsnNode).desc)
            }

            Opcodes.PUTSTATIC -> {
                if ((this as FieldInsnNode).desc.isDouble) {
                    stack.pop(2)
                } else {
                    stack.pop()
                }
            }

            Opcodes.PUTFIELD -> {
                if ((this as FieldInsnNode).desc.isDouble) {
                    stack.pop(3)
                } else {
                    stack.pop(2)
                }
            }

            in Opcodes.INVOKEVIRTUAL..Opcodes.INVOKEDYNAMIC -> {
                val desc = if (this is MethodInsnNode) this.desc else (this as InvokeDynamicInsnNode).desc
                stack.pop(Type.getArgumentTypes(desc).sumOf { it.size })

                if (opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.INVOKEDYNAMIC) {
                    stack.pop() // this ref
                }

                val a = stack.size()
                stack.psh(
                    Value.fromDesc(
                        Type.getReturnType(
                            desc
                        ).descriptor
                    )
                )
                val b = stack.size() - a
            }

            Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> stack.pop()

            Opcodes.MULTIANEWARRAY -> {
                this as MultiANewArrayInsnNode
                stack.pop(this.dims)
                stack.psh(Value.REF)
            }

            Opcodes.NEW -> Value.REF

            Opcodes.NEWARRAY, Opcodes.ANEWARRAY -> {
                stack.pop()
                stack.push(Value.REF)
            }

            Opcodes.INSTANCEOF -> {
                stack.pop()
                stack.push(Value.INT)
            }

            Opcodes.LDC -> {
                this as LdcInsnNode

                when (this.cst) {
                    is Long -> Value.LONG
                    is Double -> Value.DOUBLE

                    is Int -> Value.INT
                    is Float -> Value.FLOAT

                    is Type, is Handle,
                    is String, is ConstantDynamic -> Value.REF

                    else -> throw IllegalStateException("invalid constant: $cst")
                }
            }

            else -> return
        }

        if (value is Value) {
            stack.psh(value)
        }
    }

    private val String.isDouble
        get() = this == "D" || this == "J"

    private val AbstractInsnNode.isFork: Boolean
        get() = this is JumpInsnNode
                || this is TableSwitchInsnNode
                || this is LookupSwitchInsnNode

    private val AbstractInsnNode.isTerminating: Boolean
        get() = opcode in Opcodes.IRETURN..Opcodes.RETURN
                || opcode == Opcodes.ATHROW

    private enum class Value {
        INT, FLOAT, REF, LONG, DOUBLE, TOP, VOID;

        companion object {
            fun fromDesc(desc: String): Value =
                when (desc) {
                    "I", "S", "B", "C", "Z" -> INT
                    "F" -> FLOAT
                    "J" -> LONG
                    "D" -> DOUBLE
                    "V" -> VOID
                    else -> REF
                }
        }
    }

    private fun IntStack.psh(value: Value) =
        psh(value.ordinal)

    private fun IntStack.psh(value: Int) {
        if (value == Value.VOID.ordinal) {
            return
        }

        push(value)

        if (value == Value.LONG.ordinal || value == Value.DOUBLE.ordinal) {
            push(Value.TOP)
        }
    }
}