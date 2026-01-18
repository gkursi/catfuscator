package xyz.qweru.cat.transform.flow

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.JumpInsnNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.transformClass
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.generate.MaxLoadPool
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

class GotoReplaceTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("GotoReplace", "Replace goto opcodes with conditional jumps that always succeed", target, opts) {
    val light by value("Light", "Disables heavy obfuscation", false)
    val heavy by value("Heavy", "Disables light obfuscation", true)
    val maxVarUses by value("Max Field Use", "Max amount of uses a single generated field can have", 15)
    val exceptions by value("Exceptions", "Possible exceptions to throw", arrayListOf(
        "java/lang/Exception",
        "java/lang/RuntimeException",
        "java/lang/ArithmeticException",
        "java/lang/ArrayIndexOutOfBoundsException",
        "java/lang/ArrayStoreException",
        "java/lang/ClassCastException",
        "java/lang/ClassNotFoundException",
        "java/lang/CloneNotSupportedException",
        "java/lang/EnumConstantNotPresentException",
        "java/lang/IllegalAccessException",
        "java/lang/IllegalArgumentException",
        "java/lang/IllegalCallerException",
        "java/lang/IllegalMonitorStateException",
        "java/lang/IllegalStateException",
        "java/lang/IllegalThreadStateException",
        "java/lang/IncompatibleClassChangeError",
        "java/lang/IndexOutOfBoundsException",
        "java/lang/InstantiationException",
        "java/lang/InterruptedException",
        "java/lang/LayerInstantiationException",
        "java/lang/LinkageError",
        "java/lang/NegativeArraySizeException",
        "java/lang/NoSuchFieldException",
        "java/lang/NoSuchMethodException",
        "java/lang/NullPointerException",
        "java/lang/NumberFormatException",
        "java/lang/ReflectiveOperationException",
        "java/lang/SecurityException",
        "java/lang/StringIndexOutOfBoundsException",
        "java/lang/TypeNotPresentException",
        "java/lang/UnsupportedOperationException"
    ))

    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) continue
                val klass = entry.value

                val garbage = "garbageField${klass.name.replace("/", "_")}"

                transformClass(klass) {
                    field(garbage, Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "J", Random.nextLong())
                }

                val fields = MaxLoadPool(maxVarUses) {
                    val fieldName = "${it}gotoReplace$${klass.name.replace("/", "_")}"
                    transformClass(klass) {
                        field(fieldName, Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "J", Random.nextLong(Long.MIN_VALUE, 0L))
                    }
                    fieldName
                }

                for (method in klass.methods) {
                    transformMethod(method) {
                        replace({ it.opcode == Opcodes.GOTO }) { goto, _, _ ->
                            goto as JumpInsnNode
                            instructionsFor(method) {
                                val field = fields.getNext()
                                when (Random.nextInt(if (heavy) 1 else 0, if (light) 1 else 3)) {
                                    0 -> {
                                        getStaticField(klass.name, field, "J")
                                        ldc(Random.nextLong(Long.MAX_VALUE))
                                        compareLongs()
                                        jumpIfLessThan(goto.label)
                                    }
                                    1 -> {
                                        val negLabel = label()
                                        val nonNegLabel = label()
                                        val endLabel = label()
//
                                        getStaticField(klass.name, field, "J")
                                        ldc(Random.nextLong())
                                        andLongs()
                                        ldc(Random.nextLong(1, Long.MAX_VALUE / 1000000L))
                                        xorLongs()
                                        longConstant0()
                                        compareLongs()
                                        jumpIfLessThan(negLabel)
                                        ldc(Random.nextLong(0, Long.MAX_VALUE))
                                        +nonNegLabel
                                        long2Int()
                                        ldc(Random.nextInt())
                                        mulInts()
                                        ldc(Random.nextInt(Int.MAX_VALUE / 4))
                                        moduloInts()
                                        ldc(Random.nextInt(Int.MAX_VALUE - 1000, Int.MAX_VALUE))
                                        jumpIfIntSmaller(endLabel)
                                        +negLabel
                                        getStaticField(klass.name, field, "J")
                                        ldc(-1L)
                                        mulLongs()
                                        jump(nonNegLabel)
                                        +endLabel
                                        jump(goto.label)
                                    }
                                    2 -> {
                                        val labelA = label()
                                        val labelB = label()
                                        val labelC = label()
                                        val end = label()

                                        getStaticField(klass.name, fields.getNext(), "J")
                                        ldc(-1L)
                                        mulLongs()
                                        ldc(Random.nextLong(Long.MIN_VALUE, 0L))
                                        compareLongs()
                                        jumpIfLessThan(labelA)
                                        +labelB
                                        getStaticField(klass.name, fields.getNext(), "J")
                                        ldc(Random.nextLong(Long.MAX_VALUE))
                                        compareLongs()
                                        jumpIfGreaterEq(labelB)
                                        getStaticField(klass.name, fields.getNext(), "J")
                                        ldc(-1L)
                                        mulLongs()
                                        ldc(Random.nextLong(Long.MIN_VALUE, 0))
                                        compareLongs()
                                        jumpIfLessThan(labelA)
                                        +labelC
                                        getStaticField(klass.name, fields.getNext(), "J")
                                        ldc(Random.nextLong(Long.MAX_VALUE))
                                        compareLongs()
                                        jumpIfGreaterEq(labelB)
                                        ldc(Random.nextLong(0, Long.MAX_VALUE))
                                        ldc(Random.nextLong(1000000L))
                                        moduloLongs()
                                        jump(end)
                                        +labelA
                                        getStaticField(klass.name, fields.getNext(), "J")
                                        ldc(1L)
                                        mulLongs()
                                        ldc(Random.nextLong(1000000L))
                                        moduloLongs()
                                        +end
                                        ldc(Random.nextLong(1000000000L, Long.MAX_VALUE))
                                        compareLongs()
                                        jumpIfLessThan(goto.label)
                                    }
                                }
                                newObject(exceptions.random(), "()V") {}
                                throwEx()
                                when (Random.nextInt(3)) {
                                    0 -> {
                                        getStaticField(klass.name, field, "J")
                                        ldc(Random.nextLong())
                                        addLongs()
                                        storeStaticField(klass.name, fields.getNext(), "J")
                                    }
                                    1 -> {
                                        newObject(exceptions.random(), "()V") {}
                                        throwEx()
                                    }
                                    2 -> {
                                        val labelA = label()
                                        val labelB = label()
                                        val labelC = label()
                                        val labelD = label()
                                        ldc(Random.nextLong(0L, 100000L))
                                        +labelA
                                        ldc(Random.nextLong())
                                        xorLongs()
                                        getStaticField(klass.name, fields.getNext(), "J")
                                        xorLongs()
                                        getStaticField(klass.name, fields.getNext(), "J")
                                        moduloLongs()
                                        longConstant0()
                                        compareLongs()
                                        jumpIfLessThan(labelA)
                                        +labelB
                                        getStaticField(klass.name, field, "J")
                                        getStaticField(klass.name, fields.getNext(), "J")
                                        mulLongs()
                                        storeStaticField(klass.name, garbage, "J")
                                        jump(labelD)
                                        +labelC
                                        getStaticField(klass.name, garbage, "J")
                                        ldc(-1L)
                                        mulLongs()
                                        storeStaticField(klass.name, garbage, "J")
                                        jump(labelB)
                                        +labelD
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        parallel.await()
    }
}