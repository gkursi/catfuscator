package xyz.qweru.cat.transform.encrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.*
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.generate.exactRandomString
import xyz.qweru.cat.util.generate.nextNonZeroInt
import xyz.qweru.cat.util.generate.nextNonZeroLong
import xyz.qweru.cat.util.generate.stringLength
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

private val logger = KotlinLogging.logger {  }

class NoConstantTransformer : Transformer("ConstantHide", "Hides constants") {
    val hideSmallNumbers by value("StrLen Small Numbers", "Hides small numbers as String#length calls", false)
    val maxNumberLength by value("Max Number Length", "What is considered a small number", 5)
    val invisibleEncode by value("Invisible String", "Hide string length", true)
    val encodeNumbers by value("Stringify Numbers", "Encode numbers as strings", false)
    val encodeFloatingPoint by value("Encode Floating Point", "Encode floating-point numbers as bits", true)
    val arrayNumbers by value("Array Numbers", "Hide number constants in an array", true)
    val arrayFloatingPoint by value("Array Floating Point", "Hide number constants in an array", false)
    val numberGen by value("Number Generation", "Generate number constants in multiple steps", false)
    val genSteps by value("Generation Steps", "Number generation step count (unstable)", 0)

    val arrayObjects by value("Array Objects", "Hide strings/types in an object array", false)
//    ToDo: val stringGen by value("String Generation", "Generate string constants in multiple steps", true)

    val multiPass by value("Multi-pass", "Computes different types in separate passes to allow heavier obfuscation", true)

    private val handleNumbers
        get() = hideSmallNumbers
                || encodeNumbers
                || arrayNumbers

    override fun apply(target: JarContainer, opts: Configuration) {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) continue
                val klass = entry.value

                parallel {
                    val context = ClassContext(
                        klass.name,
                        arrayListOf(),
                        arrayListOf(),
                        arrayListOf(),
                        arrayListOf(),
                        arrayListOf(),
                        $$"\u0000noConstant$a\u0000$${Random.nextInt()}",
                        $$"\u0000noConstant$b\u0000$${Random.nextInt()}",
                        $$"\u0000noConstant$c\u0000$${Random.nextInt()}",
                        $$"\u0000noConstant$d\u0000$${Random.nextInt()}",
                        $$"\u0000noConstant$e\u0000$${Random.nextInt()}",
                    )

                    for (method in klass.methods) transformMethod(method) {
                        var pass = createPass()

                        if (handleNumbers) {
                            // this might generate new ints/longs
                            // so we compute it separately
                            pass.replace(::hasFloatingPoint) { insn, _, _ ->
                                instructionsFor(method) {
                                    handleNumber(insn, context)
                                }
                            }

                            if (multiPass) {
                                pass = createPass()
                            }

                            pass.replace(::hasNumericValue) { insn, _, _ ->
                                instructionsFor(method) {
                                    handleNumber(insn, context)
                                }
                            }

                            if (multiPass && arrayObjects) {
                                pass = createPass()
                            }
                        }

                        if (arrayObjects) {
                            pass.replace({
                                it is LdcInsnNode
                                        && it.cst !is Number
                                        && it.cst !is ConstantDynamic
                                        && !(it.cst.let { cst ->
                                                cst is Type && cst.sort == Type.METHOD
                                        })
                            }) { insn, _, _ ->
                                instructionsFor(method) {
                                    insn as LdcInsnNode
                                    getStaticField(klass.name, context.objField, "[Ljava/lang/Object;")
                                    ldc(context.objects.size)
                                    loadObjectFromArray()
                                    context.objects.add(insn.cst)
                                    when (insn.cst) {
                                        is String -> checkCast("java/lang/String")
                                        is Type -> checkCast("java/lang/Class")
                                        is Handle -> checkCast("java/lang/invoke/MethodHandle")
                                        else -> throw IllegalStateException("parsing constant ${insn.cst}")
                                    }
                                }
                            }
                        }
                    }

                    if (!context.hasAny) return@parallel
                    val clinit = klass.methods.find { it.name == "<clinit>" }

                    transformClass(klass) {
                        if (!context.ints.isEmpty()) field(context.intField, PUBLIC_STATIC, "[I", null)
                        if (!context.longs.isEmpty()) field(context.longField, PUBLIC_STATIC, "[J", null)
                        if (!context.floats.isEmpty()) field(context.floatField, PUBLIC_STATIC, "[F", null)
                        if (!context.doubles.isEmpty()) field(context.doubleField, PUBLIC_STATIC, "[D", null)
                        if (!context.objects.isEmpty()) field(context.objField, PUBLIC_STATIC, "[Ljava/lang/Object;", null)
                    }

                    if (clinit != null) {
                        transformMethod(clinit) {
                            createPass().insertHead(instructionsFor(clinit) {
                                buildArrays(context)
                            })
                        }
                    } else {
                        transformClass(klass) {
                            method("<clinit>", PUBLIC_STATIC, "()V") {
                                buildArrays(context)
                                returnVoid()
                            }
                        }
                    }
                }
            }
        }
        parallel.await()
    }

    private fun InsnBuilder.buildArrays(context: ClassContext) = with(context) {
        if (!ints.isEmpty()) {
            ldc(ints.size)
            newIntArray()

            if (numberGen) {
                val keys = ArrayList<Int>(genSteps)

                // create steps
                var keyTotal = 0

                repeat(genSteps) {
                    val nextKey = Random.nextNonZeroInt()
                        .also(keys::add)
                    keyTotal = keyTotal xor nextKey
                }

                for ((index, i) in ints.withIndex()) {
                    dup()
                    ldc(index)
                    ldc(i xor keyTotal)
                    storeIntInArray()
                }

                while (!keys.isEmpty()) {
                    val key = keys.removeFirst()
                    for (index in 0 until ints.size) {
                        dup()
                        dup()
                        ldc(index)
                        dup_x1()
                        loadIntFromArray()
                        ldc(key)
                        xorInts()
                        storeIntInArray()
                    }
                }
            } else {
                for ((index, i) in ints.withIndex()) {
                    dup()
                    ldc(index)
                    ldc(i)
                    storeIntInArray()
                }
            }

            storeStaticField(context.klass, intField, "[I")
        }

        if (!longs.isEmpty()) {
            ldc(longs.size)
            newLongArray()

            if (numberGen) {
                val keys = ArrayList<Long>(genSteps)

                // create steps
                var keyTotal = 0L

                repeat(genSteps) {
                    val nextKey = Random.nextNonZeroLong()
                        .also(keys::add)
                    keyTotal = keyTotal xor nextKey
                }

                for ((index, i) in longs.withIndex()) {
                    dup()
                    ldc(index)
                    ldc(i xor keyTotal)
                    storeLongInArray()
                }

                while (!keys.isEmpty()) {
                    val key = keys.removeFirst()
                    for (index in 0 until longs.size) {
                        dup()
                        dup()
                        ldc(index)
                        dup_x1()
                        loadLongFromArray()
                        ldc(key)
                        xorLongs()
                        storeLongInArray()
                    }
                }
            } else {
                for ((index, long) in longs.withIndex()) {
                    dup()
                    ldc(index)
                    ldc(long)
                    storeLongInArray()
                }
            }

            storeStaticField(context.klass, longField, "[J")
        }

        if (!floats.isEmpty()) {
            ldc(floats.size)
            newFloatArray()
            for ((index, float) in floats.withIndex()) {
                dup()
                ldc(index)
                ldc(float)
                storeFloatInArray()
            }
            storeStaticField(context.klass, floatField, "[F")
        }

        if (!doubles.isEmpty()) {
            ldc(doubles.size)
            newDoubleArray()

            for ((index, double) in doubles.withIndex()) {
                dup()
                ldc(index)
                ldc(double)
                storeDoubleInArray()
            }

            storeStaticField(context.klass, doubleField, "[D")
        }

        if (!objects.isEmpty()) {
            ldc(objects.size)
            newObjectArray("java/lang/Object")

            for ((index, obj) in objects.withIndex()) {
                dup()
                ldc(index)
                ldc(obj)
                storeObjectInArray()
            }

            storeStaticField(context.klass, objField, "[Ljava/lang/Object;")
        }
    }

    private fun hasNumericValue(insn: AbstractInsnNode): Boolean = when (insn) {
        is LdcInsnNode -> insn.cst !is Type
                && insn.cst !is String
                && insn.cst !is ConstantDynamic
                && insn.cst !is Handle
        is InsnNode -> insn.opcode.let {
            it == Opcodes.ICONST_0
                    || it == Opcodes.ICONST_1
                    || it == Opcodes.ICONST_M1
                    || it == Opcodes.ICONST_2
                    || it == Opcodes.ICONST_3
                    || it == Opcodes.ICONST_4
                    || it == Opcodes.ICONST_5
                    || it == Opcodes.LCONST_0
                    || it == Opcodes.LCONST_1
        }
        else -> false
    }

    private fun hasFloatingPoint(insn: AbstractInsnNode): Boolean = when (insn) {
        is LdcInsnNode -> insn.cst is Double || insn.cst is Float
        is InsnNode -> insn.opcode.let {
            it == Opcodes.FCONST_0
                    || it == Opcodes.FCONST_1
                    || it == Opcodes.DCONST_0
                    || it == Opcodes.DCONST_1
        }
        else -> false
    }

    private fun getNumericVale(insn: AbstractInsnNode): Number = when (insn) {
        is LdcInsnNode -> insn.cst as Number
        is InsnNode -> when (insn.opcode) {
            Opcodes.ICONST_0 -> 0
            Opcodes.LCONST_0 -> 0L
            Opcodes.FCONST_0 -> 0F
            Opcodes.DCONST_0 -> 0.0
            Opcodes.ICONST_1 -> 1
            Opcodes.LCONST_1 -> 1L
            Opcodes.FCONST_1 -> 1F
            Opcodes.DCONST_1 -> 1.0
            Opcodes.ICONST_2 -> 2
            Opcodes.ICONST_3 -> 3
            Opcodes.ICONST_4 -> 4
            Opcodes.ICONST_5 -> 5
            Opcodes.ICONST_M1 -> -1
            else -> throw IllegalArgumentException()
        }
        else -> throw IllegalArgumentException()
    }

    private fun InsnBuilder.handleNumber(insn: AbstractInsnNode, context: ClassContext) =
        when (val value = getNumericVale(insn)) {
            is Int -> handleInt(value, context)
            is Float -> handleFloat(value, context)
            is Double -> handleDouble(value, context)
            is Long -> handleLong(value, context)
            else -> throw IllegalStateException()
        }

    private fun InsnBuilder.handleInt(int: Int, context: ClassContext) {
        if (hideSmallNumbers && abs(int) < maxNumberLength) {
            ldc(createString(abs(int)))
            stringLength()
            if (int < 0) {
                ldc(-1)
                mulInts()
            }
            return
        }

        if (!encodeNumbers && !arrayNumbers) {
            ldc(int)
            return
        }

        val choices = arrayListOf<() -> Unit>()
        
        if (arrayNumbers) choices.add {
            getStaticField(context.klass, context.intField, "[I")
            ldc(context.ints.size)
            loadIntFromArray()
            context.ints.add(int)
        }

        if (encodeNumbers) choices.add {
            ldc(int.toString())
            invokeStatic("java/lang/Integer", "parseInt", "(Ljava/lang/String;)I")
        }

        choices.random()()
    }

    private fun InsnBuilder.handleFloat(float: Float, context: ClassContext) {
        if (!arrayNumbers && !encodeNumbers) {
            ldc(float)
            return
        }

        val choices = arrayListOf<() -> Unit>()

        if (arrayFloatingPoint) choices.add {
            getStaticField(context.klass, context.floatField, "[F")
            ldc(context.floats.size)
            loadFloatFromArray()
            context.floats.add(float)
        }

        if (encodeFloatingPoint) choices.add {
            ldc(float.toBits())
            invokeStatic("java/lang/Float", "intBitsToFloat", "(I)F")
        }

        choices.random()()
    }

    private fun InsnBuilder.handleDouble(double: Double, context: ClassContext) {
        if (!arrayNumbers && !encodeNumbers) {
            ldc(double)
            return
        }

        val choices = arrayListOf<() -> Unit>()

        if (arrayFloatingPoint) choices.add {
            getStaticField(context.klass, context.doubleField, "[D")
            ldc(context.doubles.size)
            loadDoubleFromArray()
            context.doubles.add(double)
        }

        if (encodeFloatingPoint) choices.add {
            ldc(double.toBits())
            invokeStatic("java/lang/Double", "longBitsToDouble", "(J)D")
        }

        choices.random()()
    }

    private fun InsnBuilder.handleLong(long: Long, context: ClassContext) {
        if (hideSmallNumbers && abs(long) < maxNumberLength.toLong()) {
            ldc(createString(abs(long.toInt())))
            stringLength()
            if (long < 0) {
                ldc(-1)
                mulInts()
            }
            int2Long()
            return
        }

        if (!encodeNumbers && !arrayNumbers) {
            ldc(long)
            return
        }

        val choices = arrayListOf<() -> Unit>()

        if (arrayNumbers) choices.add {
            getStaticField(context.klass, context.longField, "[J")
            ldc(context.longs.size)
            loadLongFromArray()
            context.longs.add(long)
        }

        if (encodeNumbers) choices.add {
            ldc(long.toString())
            invokeStatic("java/lang/Long", "parseLong", "(Ljava/lang/String;)J")
        }

        choices.random()()
    }

    private fun createString(length: Int) =
        if (invisibleEncode) "\u0000".repeat(length) else exactRandomString(length)
    
    private data class ClassContext(
        val klass: String,
        val ints: ArrayList<Int>,
        val floats: ArrayList<Float>,
        val doubles: ArrayList<Double>,
        val longs: ArrayList<Long>,
        val objects: ArrayList<Any>,
        val intField: String,
        val floatField: String,
        val doubleField: String,
        val longField: String,
        val objField: String
    ) {
        val hasAny: Boolean
            get() = !ints.isEmpty()
                    || !floats.isEmpty()
                    || !doubles.isEmpty()
                    || !longs.isEmpty()
    }

}
