package xyz.qweru.cat.util.analysis

import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode

val AbstractInsnNode.stackChange: Int
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

val String.isDoubleDesc: Boolean
    get() = this == "J" || this == "D"

val AbstractInsnNode.isFork: Boolean
    get() = this is JumpInsnNode
            || this is TableSwitchInsnNode
            || this is LookupSwitchInsnNode

val AbstractInsnNode.dsc
    get() = when (this) {
        is MethodInsnNode -> desc
        is InvokeDynamicInsnNode -> desc
        else -> throw IllegalArgumentException()
    }

val AbstractInsnNode.isTerminating: Boolean
    get() = opcode in Opcodes.IRETURN..Opcodes.RETURN
            || opcode == Opcodes.ATHROW
            || opcode == Opcodes.GOTO
            || opcode == Opcodes.TABLESWITCH
            || opcode == Opcodes.LOOKUPSWITCH