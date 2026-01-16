package xyz.qweru.cat.transform.encrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.*
import xyz.qweru.cat.util.asm.CatMethodParameter
import xyz.qweru.cat.util.asm.InsnBuilder
import xyz.qweru.cat.util.asm.MethodTransformer
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.newClass
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.asm.versionFromJar
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class StringEncryptTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("StringEncrypt", "Encrypt strings", target, opts) {

    val encryptConcat by value("Encrypt Concat", "Also encrypts string concatenation", true)

    val classNode by lazy {
        newClass(
            "cat/CatStringObfuscator",
            versionFromJar(target),
            Opcodes.ACC_PUBLIC
        ) {
            method("<init>", Opcodes.ACC_PUBLIC, "()V") {
                loadLocalObject(0)
                invokeSpecial("java/lang/Object", "<init>", "()V")
                returnVoid()
            }

            method(
                "decrypt",
                Opcodes.ACC_PUBLIC,
                "(Ljava/lang/String;I)Ljava/lang/String;",
                parameters = listOf(
                    CatMethodParameter(
                        ParameterNode("target", Opcodes.ACC_FINAL),
                        "Ljava/lang/String;"
                    ),
                    CatMethodParameter(
                        ParameterNode("key", Opcodes.ACC_FINAL),
                        "I"
                    )
                )
            ) {
                val start = startLabel
                val end = endLabel
                val loopSetup = label()
                val loopContent = label()

                val targetString = 1
                val key = 2
                val chars = local("chars", "[C", start, end)
                val length = local("size", "I", start, end)
                val iterator = local("i", "I", loopSetup, loopContent)

                // store char array and its size
                loadLocalObject(targetString) // load target string
                invokeVirtual("java/lang/String", "toCharArray", "()[C")
                dup()
                storeLocalObject(chars)
                getArraySize()
                storeLocalInt(length)

                setLabel(loopSetup)
                loadConstant(0)
                storeLocalInt(iterator)

                // xor loop
                setLabel(loopContent)

                // setup array for loading and then storing
                loadLocalObject(chars)
                loadLocalInt(iterator)
                dup2()

                loadCharFromArray() // aref, i, aref, i -> aref, i, char
                loadLocalInt(key) // load key
                loadLocalInt(iterator)
                addInts()
                xorInts() // aref, i, char, key -> aref, i, xored char
                storeCharInArray() // aref, i, char -> empty stack

                incrementLocalInt(iterator, 1)
                loadLocalInt(iterator)
                loadLocalInt(length)
                jumpIfSmaller(loopContent)

                newObject("java/lang/String", "([C)V") {
                    loadLocalObject(chars)
                }
                returnInstance()
            }
        }
    }

    init {
        val parallel = createExecutorFrom(opts)

        target.apply {
            for (node in classes.entries) {
                if (!canTarget(node)) continue
                val klass = node.value
                parallel {
                    for (method in klass.methods) {
                        transformMethod(method) {
                            transformLdc(method)
                            transformIndy(method)
                        }
                    }
                }
            }
            parallel.await()
            classes[classNode.name] = classNode
        }
    }

    private fun isStringConcatFactory(indy: InvokeDynamicInsnNode): Boolean
        = indy.name == "makeConcatWithConstants" && indy.bsm.owner == "java/lang/invoke/StringConcatFactory"

    private fun MethodTransformer.transformLdc(method: MethodNode) {
        replace({ it is LdcInsnNode && it.cst is String }) { insn, _, _ ->
            instructionsFor(method) {
                handleString((insn as LdcInsnNode).cst as String)
            }
        }
    }

    private fun MethodTransformer.transformIndy(method: MethodNode) {
        if (!encryptConcat) return
        replace(predicate = { it is InvokeDynamicInsnNode && isStringConcatFactory(it)}) { indy, _, _ ->
            indy as InvokeDynamicInsnNode
            val recipe = indy.bsmArgs[0] as String

            val concatString = recipe.replace("\u0001", "%s")
            val concatArgSize = recipe.count { it == '\u0001' }

            val types = Type.getArgumentTypes(indy.desc)
            logger.info { "InDy -> String $concatString (types=[${
                types.joinToString(", ")  
            }])" }

            instructionsFor(method) {
                loadConstant(concatArgSize)
                newArray("java/lang/Object")
                dup()

                for (i in 0..<concatArgSize) {
                    val typeSort = types[i].sort
                    if (isDoubleSize(typeSort)) {
                        // stack: ... value i-1, value i 1/2, value i 2/2, array, array
                        dup2_x2()
                        // stack: ... value i-1, array, array, value i 1/2, value i 2/2, array, array
                        pop2()
                        // stack: ... value i-1, array, array, value i 1/2, value i 2/2
                        loadConstant(i)
                        // stack: ... value i-1, array, array, value i 1/2, value i 2/2, index
                        dup_x2()
                        // stack: ... value i-1, array, array, index, value i 1/2, value i 2/2, index
                        pop()
                        // stack: ... value i-1, array, array, index, value i 1/2, value i 2/2
                    } else {
                        // stack: ... value i-1, value i, array, array
                        dup2_x1()
                        // stack: ... value i-1, array, array, value i, array, array
                        pop2()
                        // stack: ... value i-1, array, array, value i
                        loadConstant(i)
                        // stack: ... value i-1, array, array, value i, index
                        swap()
                        // stack: ... value i-1, array, array, index, value i
                    }

                    boxPrimitive(typeSort)
                    storeObjectInArray()
                    // stack: ... value i-1, array
                    dup()
                    // stack: ... value i-1, array, array
                }

                // stack: ..., array, array
                pop()
                // stack: ..., array

                handleString(concatString.toString())
                // stack: ..., array, string
                swap()
                // stack: ..., string, array
                invokeStatic("java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")
                // stack: ..., string
            }
        }
    }

    private fun isDoubleSize(type: Int) =
        type == Type.LONG || type == Type.DOUBLE

    private fun InsnBuilder.boxPrimitive(type: Int) = when(type) {
        Type.OBJECT, Type.ARRAY -> {}
        Type.INT -> invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
        Type.FLOAT -> invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;")
        Type.BOOLEAN -> invokeStatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;")
        Type.BYTE -> invokeStatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;")
        Type.SHORT -> invokeStatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;")
        Type.LONG -> invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
        Type.DOUBLE -> invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;")
        else -> throw IllegalArgumentException("Unknown type sort: $type")
    }

    /**
     * The resulting instructions will push the passed string to the stack
     */
    private fun InsnBuilder.handleString(string: String) {
        val key = Random.nextInt(Int.MIN_VALUE, Int.MAX_VALUE)

        newObject(classNode.name, "()V") {}
        loadConstant(xor(string, key))
        loadConstant(key)
        invokeVirtual(classNode.name, "decrypt", "(Ljava/lang/String;I)Ljava/lang/String;")
    }

    private fun xor(string: String, key: Int): String {
        val chars = string.toCharArray()
        for ((index, ch) in chars.withIndex()) {
            chars[index] = (ch.code xor (key + index)).toChar()
        }
        return String(chars)
    }
}