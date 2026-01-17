package xyz.qweru.cat.transform.encrypt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.*
import xyz.qweru.cat.util.generate.LoadBalancePool
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class StringEncryptTransformer(
    target: JarContainer,
    opts: Configuration,
) : Transformer("StringEncrypt", "Encrypt strings", target, opts) {
    val encryptConst by value("Encrypt Constants", "Encrypt string constants", true)
    val encryptConcat by value("Encrypt Concat", "Encrypt string concatenation (will slow it down a LOT)", true)
    val poolLimit by value("String Pool Limit", "Max strings per pool", 4)

    private val classPool = LoadBalancePool(poolLimit) {
        newClass(
            "cat/StringPool$it",
            versionFromJar(target),
            Opcodes.ACC_PUBLIC
        ) {
            method("<init>", Opcodes.ACC_PUBLIC, "(JJJJ)V") {
                val _this = this@newClass.classNode.name
                val l0 = 1
                val l1 = 3
                val l2 = 5
                val l3 = 7

                loadLocalObject(0)
                invokeSpecial("java/lang/Object", "<init>", "()V")

                loadLocalObject(0)
                getStaticField(_this, "pool0", "J")
                loadLocalLong(l0)
                xorLongs()
                loadLocalLong(l3)
                getStaticField(_this, "k", "J")
                xorLongs()
                andLongs()
                storeField(_this, "xorPool0", "J")

                loadLocalObject(0)
                getStaticField(_this, "pool1", "J")
                loadLocalLong(l1)
                xorLongs()
                loadLocalLong(l2)
                getStaticField(_this, "k", "J")
                xorLongs()
                andLongs()
                storeField(_this, "xorPool1", "J")

                loadLocalObject(0)
                getStaticField(_this, "pool2", "J")
                loadLocalLong(l2)
                xorLongs()
                loadLocalLong(l1)
                getStaticField(_this, "k", "J")
                xorLongs()
                andLongs()
                storeField(_this, "xorPool2", "J")

                loadLocalObject(0)
                getStaticField(_this, "pool3", "J")
                loadLocalLong(l3)
                xorLongs()
                loadLocalLong(l0)
                getStaticField(_this, "k", "J")
                xorLongs()
                andLongs()
                storeField(_this, "xorPool3", "J")

                returnVoid()
            }

            field("k", Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "J", Random.nextLong())

            field("pool0", Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "J", Random.nextLong())
            field("xorPool0", Opcodes.ACC_PUBLIC, "J", Random.nextLong())

            field("pool1", Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "J", Random.nextLong())
            field("xorPool1", Opcodes.ACC_PUBLIC, "J", Random.nextLong())

            field("pool2", Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "J", Random.nextLong())
            field("xorPool2", Opcodes.ACC_PUBLIC, "J", Random.nextLong())

            field("pool3", Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "J", Random.nextLong())
            field("xorPool3", Opcodes.ACC_PUBLIC, "J", Random.nextLong())


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

                +loopSetup
                loadConstant(0)
                storeLocalInt(iterator)

                // xor loop
                +loopContent

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

                val buildLongs = label()
                val longsLoop = label()

                val _this = this@newClass.classNode.name
                val strings = local("strings", "[Ljava/lang/String;", buildLongs, end)
                val stringsSize = local("ssize", "I", buildLongs, end)
                val stringIter = local("it", "I", buildLongs, end)
                val stringBuilder = local("builder", "Ljava/lang/StringBuilder;", buildLongs, end)

                +buildLongs
                newObject("java/lang/StringBuilder", "()V") {}
                storeLocalObject(stringBuilder)
                newObject("java/lang/String", "([C)V") {
                    loadLocalObject(chars)
                }

                ldc(";")
                invokeVirtual("java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;")
                dup()

                dup()
                storeLocalObject(strings)
                arrayLength()
                storeLocalInt(stringsSize)

                constant0()
                storeLocalInt(stringIter)

                +longsLoop
                loadLocalObject(stringBuilder)

                // load string
                loadLocalObject(strings)
                loadLocalInt(stringIter)
                loadObjectFromArray()
                // parse to long
                invokeStatic("java/lang/Long", "parseLong", "(Ljava/lang/String;)J")
                loadLocalObject(0)

                loadLocalInt(stringIter)
                constant4()
                moduloInts()

                val case0 = label()
                val case1 = label()
                val case2 = label()
                val case3 = label()
                val switchEnd = label()

                tableSwitch(0, 2, case3, case0, case1, case2)

                +case0
                getField(_this, "xorPool0", "J")
                jump(switchEnd)

                +case1
                getField(_this, "xorPool1", "J")
                jump(switchEnd)

                +case2
                getField(_this, "xorPool2", "J")
                jump(switchEnd)

                +case3
                getField(_this, "xorPool3", "J")
                jump(switchEnd)

                val theLong = local("tl", "J", switchEnd, switchEnd)
                +switchEnd
                xorLongs()
                storeLocalLong(theLong)

                newObject("java/lang/String", "([B)V") {
                    loadLocalObject(0)
                    loadLocalLong(theLong)
                    invokeVirtual(_this, "longToBytes", "(J)[B")
                }
                invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
                storeLocalObject(stringBuilder)

                incrementLocalInt(stringIter, 1)
                loadLocalInt(stringIter)
                loadLocalInt(stringsSize)
                jumpIfSmaller(longsLoop)

                +label()
                loadLocalObject(stringBuilder)
                invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
                returnInstance()
            }

            method(
                "longToBytes",
                Opcodes.ACC_PUBLIC,
                "(J)[B",
                parameters = listOf(
                    CatMethodParameter(
                        ParameterNode("long", Opcodes.ACC_FINAL),
                        "J"
                    )
                )
            ) {
                /*
                    fun longToBytes(l: Long): ByteArray {
                        var long = l
                        val result = ByteArray(Long.SIZE_BYTES)
                        for (i in Long.SIZE_BYTES - 1 downTo 0) {
                            result[i] = (long and 0xFF).toByte()
                            long = long shr Byte.SIZE_BITS
                        }
                        return result
                    }
                 */
                val param = 1
                val theLong = local("theLong", "J", startLabel, endLabel)
                val result = local("result", "[B", startLabel, endLabel)

                loadLocalLong(param)
                storeLocalLong(theLong)
                ldc(8)
                newByteArray()
                storeLocalObject(result)

                val loop = label()
                val i = local("i", "I", startLabel, endLabel)
                ldc(7)
                storeLocalInt(i)

                +loop
                loadLocalObject(result)
                loadLocalInt(i)
                loadLocalLong(theLong)
                ldc(0xFFL)
                andLongs()
                long2Int()
                int2Byte()
                storeByteInArray()

                loadLocalLong(theLong)
                loadConstant(8)
                shrLongs()
                storeLocalLong(theLong)

                incrementLocalInt(i, -1)
                loadLocalInt(i)
                constant0()
                jumpIfGreaterEq(loop)

                loadLocalObject(result)
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
                            transformLdc(method, klass)
                            transformIndy(method, klass)
                        }
                    }
                }
            }
            parallel.await()
            classPool.iterator().forEach(::put)
        }
    }

    private fun isStringConcatFactory(indy: InvokeDynamicInsnNode): Boolean
        = indy.name == "makeConcatWithConstants" && indy.bsm.owner == "java/lang/invoke/StringConcatFactory"

    private fun MethodTransformer.transformLdc(method: MethodNode, klass: ClassNode) {
        if (!encryptConst) return
        replace({ it is LdcInsnNode && it.cst is String }) { insn, _, _ ->
            instructionsFor(method) {
                handleString((insn as LdcInsnNode).cst as String, klass)
            }
        }
    }

    private fun MethodTransformer.transformIndy(method: MethodNode, klass: ClassNode) {
        if (!encryptConcat) return
        replace(predicate = { it is InvokeDynamicInsnNode && isStringConcatFactory(it)}) { indy, _, _ ->
            indy as InvokeDynamicInsnNode
            val recipe = indy.bsmArgs[0] as String

            val concatString = recipe.replace("\u0001", "%s")
            val concatArgSize = recipe.count { it == '\u0001' }

            val types = Type.getArgumentTypes(indy.desc)
            logger.info { "InDy : $concatString (types=[${
                types.joinToString(", ")  
            }])" }

            instructionsFor(method) {
                loadConstant(concatArgSize)
                newObjectArray("java/lang/Object")
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

                handleString(concatString, klass)
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
    private fun InsnBuilder.handleString(string: String, node: ClassNode) {
        val key = Random.nextInt(Int.MIN_VALUE, Int.MAX_VALUE)
        val node = classPool.getNext()

        val k0 = Random.nextLong()
        val k1 = Random.nextLong()
        val k2 = Random.nextLong()
        val k3 = Random.nextLong()

        newObject(node.name, "(JJJJ)V") {
            loadConstant(k0)
            loadConstant(k1)
            loadConstant(k2)
            loadConstant(k3)
        }

        val k = node.fields[0].value as Long
        val pool0 = node.fields[1].value as Long
        val pool1 = node.fields[3].value as Long
        val pool2 = node.fields[5].value as Long
        val pool3 = node.fields[7].value as Long

        val l0 = (pool0 xor k0) and (k3 xor k)
        val l1 = (pool1 xor k1) and (k2 xor k)
        val l2 = (pool2 xor k2) and (k1 xor k)
        val l3 = (pool3 xor k3) and (k0 xor k)

        val builder = StringBuilder()

        for ((index, block) in getInBlocks(string).withIndex()) {
            val b = when (index % 4) {
                0 -> l0
                1 -> l1
                2 -> l2
                3 -> l3
                else -> throw IllegalStateException("invalid modulo 4 branch")
            }
            val c = bytesToLong(block)
            val a = b xor c
            logger.info { "$a ^ $b == $c : ${a xor b == c} " }
            builder.append(a).append(';')
        }

        val output = builder.toString()

        loadConstant(xor(output, key))
        loadConstant(key)
        invokeVirtual(node.name, "decrypt", "(Ljava/lang/String;I)Ljava/lang/String;")
    }

    private fun getInBlocks(string: String, blockSize: Int = 8) =
        string.encodeToByteArray()
            .asList()
            .chunked(blockSize) { chunk ->
                ByteArray(blockSize).also { dst ->
                    for (i in chunk.indices) dst[i] = chunk[i]
                }
            }

    private fun xor(string: String, key: Int): String {
        val chars = string.toCharArray()
        for ((index, ch) in chars.withIndex()) {
            chars[index] = (ch.code xor (key + index)).toChar()
        }
        return String(chars)
    }

    fun bytesToLong(b: ByteArray): Long {
        var result: Long = 0
        for (i in 0..<Long.SIZE_BYTES) {
            result = result shl Byte.SIZE_BITS
            result = result or (b[i].toInt() and 0xFF).toLong()
        }
        return result
    }

    fun longToBytes(l: Long): ByteArray {
        var long = l
        val result = ByteArray(Long.SIZE_BYTES)
        for (i in Long.SIZE_BYTES - 1 downTo 0) {
            result[i] = (long and 0xFF).toByte()
            long = long shr Byte.SIZE_BITS
        }
        return result
    }

}