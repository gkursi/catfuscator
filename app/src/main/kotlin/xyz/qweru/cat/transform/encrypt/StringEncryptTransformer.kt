package xyz.qweru.cat.transform.encrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.ParameterNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.CatMethodParameter
import xyz.qweru.cat.util.InsnBuilder
import xyz.qweru.cat.util.createExecutorFrom
import xyz.qweru.cat.util.instructions
import xyz.qweru.cat.util.newClass
import xyz.qweru.cat.util.transformMethod
import xyz.qweru.cat.util.versionFromJar
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class StringEncryptTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("StringEncrypt", "Encrypt strings", target, opts) {

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
                            replace(listProvider = { ldc -> instructions(method) {
                                handleString((ldc as LdcInsnNode).cst as String)
                            } }, {
                                it is LdcInsnNode && it.cst is String
                            })
                        }
                    }
                }
            }
            parallel.await()
            classes[classNode.name] = classNode
        }
    }

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