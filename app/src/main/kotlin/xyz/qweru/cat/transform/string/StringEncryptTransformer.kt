package xyz.qweru.cat.transform.string

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.*
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.generate.MaxLoadPool
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

class StringEncryptTransformer(
    target: JarContainer,
    opts: Configuration,
) : Transformer("StringEncrypt", "Encrypt strings", target, opts) {
    val encryptConst by value("Encrypt Constants", "Encrypt string constants", true)
    val encryptConcat by value("Encrypt Concat", "Encrypt string concatenation (will slow it down)", true)
    val stringLimit by value("Class Limit", "Max strings per generated class", 10)
    val intern by value("Intern", "Interns strings for slightly better performance", true)

    val keyPool by value("Key Pool", "XOR key pool", true)
    val extraKey by value("Dynamic Key", "Dynamic key", true)
    val moreKeyOps by value("More Key Ops", "Uses methods besides XOR to encrypt with keys", true)

    private val initDesc: String
        get() = "(${
            if(keyPool) "JJJJ"
            else ""
        })V"
    
    private val decryptDesc: String
        get() = "(Ljava/lang/String;II)Ljava/lang/String;"

    private val keyOpLen = 2

    private val classPool = MaxLoadPool(stringLimit) {
        val globalKey = Random.nextInt(Int.MAX_VALUE)
        val keyOps = Random.nextInt(keyOpLen) to Random.nextInt(keyOpLen)

        val klass = newClass(
            "cat/StringPool$it",
            versionFromJar(target),
            Opcodes.ACC_PUBLIC
        ) {
            method(
                "<init>",
                Opcodes.ACC_PUBLIC,
                initDesc
            ) {
                val _this = this@newClass.classNode.name
                val l0 = 1
                val l1 = 3
                val l2 = 5
                val l3 = 7

                loadLocalObject(0)
                invokeSpecial("java/lang/Object", "<init>", "()V")

                if (keyPool) {
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
                }

                returnVoid()
            }

            if (keyPool) {
                field("k", PUBLIC_STATIC, "J", Random.nextLong())

                field("pool0", PUBLIC_STATIC, "J", Random.nextLong())
                field("xorPool0", Opcodes.ACC_PUBLIC, "J", Random.nextLong())

                field("pool1", PUBLIC_STATIC, "J", Random.nextLong())
                field("xorPool1", Opcodes.ACC_PUBLIC, "J", Random.nextLong())

                field("pool2", PUBLIC_STATIC, "J", Random.nextLong())
                field("xorPool2", Opcodes.ACC_PUBLIC, "J", Random.nextLong())

                field("pool3", PUBLIC_STATIC, "J", Random.nextLong())
                field("xorPool3", Opcodes.ACC_PUBLIC, "J", Random.nextLong())
            }

            method(
                "decrypt",
                Opcodes.ACC_PUBLIC,
                decryptDesc,
                parameters = listOf(
                    CMethodParameter(
                        ParameterNode("target", Opcodes.ACC_FINAL),
                        "Ljava/lang/String;"
                    ),
                    CMethodParameter(
                        ParameterNode("key", Opcodes.ACC_FINAL),
                        "I"
                    ),
                    CMethodParameter(
                        ParameterNode("nullBytes", Opcodes.ACC_FINAL),
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
                val nullBytes = 3
                val chars = local("chars", "[C", start, end)
                val length = local("size", "I", start, end)
                val iterator = local("i", "I", loopSetup, loopContent)
                val dynamicKey by lazy { local("dynamicKey", "I", start, end) }

                // store char array and its size
                loadLocalObject(targetString) // load target string
                invokeVirtual("java/lang/String", "toCharArray", "()[C")
                dup()
                storeLocalObject(chars)
                getArraySize()
                dup()
                constant0()
                val elseLabel = label()
                jumpIfIntNotEqual(elseLabel)
                ldc("")
                returnInstance()
                +elseLabel
                storeLocalInt(length)

                if (extraKey) {
                    /*
                        dkey = (globalKey ^ string len) & 0x7FFFFFFF | 1
                     */
                    ldc(globalKey)
                    loadLocalInt(length)
                    xorInts()
                    ldc(0x7FFFFFFF)
                    andInts()
                    constant1()
                    orInts()
                    storeLocalInt(dynamicKey)
                }

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
                createIntOp(keyOps.first) // aref, i, char, key -> aref, i, xored char

                if (extraKey) {
                    loadLocalInt(dynamicKey)
                    createIntOp(keyOps.first)
                    /*
                        dkey = (dkey ^ i) - key
                     */
                    loadLocalInt(dynamicKey)
                    loadLocalInt(iterator)
                    xorInts()
                    loadLocalInt(key)
                    negInt()
                    addInts()
                    storeLocalInt(dynamicKey)
                }

                storeCharInArray() // aref, i, char -> empty stack

                incrementLocalInt(iterator, 1)
                loadLocalInt(iterator)
                loadLocalInt(length)
                jumpIfIntSmaller(loopContent)

                val buildLongs = label()
                val longsLoop = label()

                val _this = this@newClass.classNode.name
                val strings = local("strings", "[Ljava/lang/String;", buildLongs, end)
                val stringsSize = local("ssize", "I", buildLongs, end)
                val stringIter = local("it", "I", buildLongs, end)
                val finalStringArray = local("builder", "[C", buildLongs, end)
                val stringBuilderIndex = local("builderIndex", "I", buildLongs, end)
                val parseResult = local("parsed", "J", start, end)
                val pooledLong = local("tl", "J", longsLoop, end)

                +buildLongs
                constant0()
                storeLocalInt(stringBuilderIndex)
                loadLocalInt(length)
                newCharArray()
                storeLocalObject(finalStringArray)
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

                // load string
                loadLocalObject(strings)
                loadLocalInt(stringIter)
                loadObjectFromArray()

                // parse to long
                invokeStatic("java/lang/Long", "parseLong", "(Ljava/lang/String;)J")
                storeLocalLong(parseResult)

                if (keyPool) {
                    loadLocalInt(stringIter)
                    constant5()
                    moduloInts()

                    val case0 = label()
                    val case1 = label()
                    val case2 = label()
                    val case3 = label()
                    val switchEnd = label()

                    tableSwitch(0, 2, case3, case0, case1, case2)

                    +case0
                    loadLocalObject(0)
                    getField(_this, "xorPool0", "J")
                    storeLocalLong(pooledLong)
                    jump(switchEnd)

                    +case1
                    loadLocalObject(0)
                    getField(_this, "xorPool1", "J")
                    storeLocalLong(pooledLong)
                    jump(switchEnd)

                    +case2
                    loadLocalObject(0)
                    getField(_this, "xorPool2", "J")
                    storeLocalLong(pooledLong)
                    jump(switchEnd)

                    +case3
                    loadLocalObject(0)
                    getField(_this, "xorPool3", "J")
                    storeLocalLong(pooledLong)
                    jump(switchEnd)

                    +switchEnd
                }

                loadLocalLong(parseResult)

                if (keyPool) {
                    loadLocalLong(pooledLong)
                    createLongOp(keyOps.second)
                }

                storeLocalLong(pooledLong)

                newObject("java/lang/String", "([B)V") {
                    loadLocalObject(0)
                    loadLocalLong(pooledLong)
                    invokeVirtual(_this, "longToBytes", "(J)[B")
                    val skip = label()
                    loadLocalInt(stringIter)
                    loadLocalInt(stringsSize)
                    constantM1()
                    addInts()
                    jumpIfIntNotEqual(skip)
                    dup()
                    arrayLength()
                    loadLocalInt(nullBytes)
                    subInts()
                    invokeStatic("java/util/Arrays", "copyOf", "([BI)[B")
                    +skip
                }

                // stack: string
                loadLocalObject(finalStringArray)
                swap() // .., chArray, string
                invokeVirtual("java/lang/String", "toCharArray", "()[C") // .., ca1, ca2
                swap()
                dup2() // ca2 ca1 ca2 ca1
                arrayLength() // ca2 ca1 ca2 l1
                swap() // ca2 ca1 l1 ca2
                arrayLength() // ca2 ca1 l1 l2
                swap() // ca2 ca1 l2 l1
                dup_x1() // ca2 ca1 l1 l2 l1
                addInts() // ca2 ca1 l1 l
                newCharArray() // ca2 ca1 l1 ca3
                dup()
                storeLocalObject(finalStringArray)
                dup_x2() // ca2 ca3 ca1 l1 ca3
                constant0() // ca2 ca3 ca1 l1 ca3 0
                swap() // ca2 ca3 ca1 l1 0 ca3
                dup2_x1();pop2() // ca2 ca3 ca1 0 ca3 l1
                constant0() // ca2 ca3 ca1 0 ca3 l1 0
                swap() // ca2 ca3 ca1 0 ca3 0 l1
                dup()
                storeLocalInt(stringBuilderIndex)
                invokeStatic("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V")
                // ca2 ca3
                swap() // 3 2
                dup() // 3 2 2
                arrayLength() // 3 2 l2
                dup_x2();pop() // l2 3 2
                swap() // l2 c2 c3
                dup2_x1();pop2() // c2 c3 l2
                constant0() // c2 c3 l2 0
                dup_x2() // c2 0 c3 l2 0
                pop() // c2 0 c3 l2
                loadLocalInt(stringBuilderIndex) // c2 0 c3 l2 l1
                swap() // c2 0 c3 l1 l2
                invokeStatic("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V")

                incrementLocalInt(stringIter, 1)
                loadLocalInt(stringIter)
                loadLocalInt(stringsSize)
                jumpIfIntSmaller(longsLoop)

                newObject("java/lang/String", "([C)V") {
                    loadLocalObject(finalStringArray)
                }

                if (intern) {
                    invokeVirtual("java/lang/String", "intern", "()Ljava/lang/String;")
                }

                returnInstance()
            }

            method(
                "longToBytes",
                Opcodes.ACC_PUBLIC,
                "(J)[B",
                parameters = listOf(
                    CMethodParameter(
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
                jumpIfIntGreaterEq(loop)

                loadLocalObject(result)
                returnInstance()
            }
        }

        if (extraKey) {
            klass.metadata[0] = globalKey
        }

        if (moreKeyOps) {
            klass.metadata[1] = keyOps
        }

        return@MaxLoadPool klass
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
            classPool.iterator()
                .forEach(this::put)
        }
    }

    private fun isStringConcatFactory(indy: InvokeDynamicInsnNode): Boolean
        = indy.name == "makeConcatWithConstants" && indy.bsm.owner == "java/lang/invoke/StringConcatFactory"

    private fun MethodTransformer.transformLdc(method: MethodNode) {
        if (!encryptConst) return
        createPass().replace({ it is LdcInsnNode && it.cst is String }) { insn, _, _ ->
            instructionsFor(method) {
                handleString((insn as LdcInsnNode).cst as String)
            }
        }
    }

    private fun MethodTransformer.transformIndy(method: MethodNode) {
        if (!encryptConcat) return
        createPass().replace(predicate = { it is InvokeDynamicInsnNode && isStringConcatFactory(it)}) { indy, _, _ ->
            indy as InvokeDynamicInsnNode
            val recipe = indy.bsmArgs[0] as String

            val concatString = recipe.replace("\u0001", "%s")
            val concatArgSize = recipe.count { it == '\u0001' }

            val types = Type.getArgumentTypes(indy.desc)

            instructionsFor(method) {
                createArrayFromStack(types, concatArgSize)
                // stack: ..., array

                handleString(concatString)
                // stack: ..., array, string
                swap()
                // stack: ..., string, array
                invokeStatic("java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")
                // stack: ..., string
            }
        }
    }

    /**
     * Consumes two ints on the stack and pushes a new int
     */
    private fun InsnBuilder.createIntOp(keyOp: Int) =
        when (if (moreKeyOps) keyOp else 0) {
            0 -> xorInts()
            1 -> {
                ldc(15)
                andInts() // a, b
                dup2() // a, b, a, b
                swap() // a, b, b, a
                ldc(0xFFFF)
                andInts()
                swap() // a, b, a&.., b
                uShrInts() // a, b, a&>>>b
                dup_x2()
                pop() // a&>b, a, b
                ldc(16)
                swap() // a&>b, a, 16, b
                subInts() // a&>b, a, 16-b
                swap() // a&>b, 16-b, a
                ldc(0xFFFF)
                andInts() // a&>b, 16-b, a&..
                swap() // a&>b, a&.., 16-b
                shlInts() // a&>>b, a&<b
                orInts()
                ldc(0xFFFF)
                andInts()
            }
            2 -> {
                swap() // key, char
                dup() // key, char, char
                ldc(16)
                shlInts() // key, char, char<<16
                swap() // key, char<<16, char
                dup() // key, char<<16, char, char
                ldc(16)
                uShrInts() // key, char<<16, char, char>>>16
                swap() // k, c<16, c>>16, char
                dup_x2()
                pop() // k, x, char<<16, char>>>16
                orInts() // k, x, y
                dup2_x1()
                pop2() // x, y, k
                ldc(4)
                uShrInts()
                constant1()
                andInts()
                negInt() // x, y, m
                dup_x1() // x, m, y, m
                andInts() // x, m, y`
                dup_x2() // y`, x, m
                pop()
                constantM1()
                xorInts() // y`, x, ~m
                andInts() // y`, x`
                swap() // x`, y`
                orInts()
            }
            else -> throw IllegalStateException("$keyOp")
        }

    private fun InsnBuilder.createLongOp(keyOp: Int) =
        when (if (moreKeyOps) keyOp else 0) {
            0, 2 -> xorLongs()
            1 -> {
                long2Int()
                invokeStatic(
                    "java/lang/Long",
                    "rotateRight",
                    "(JI)J"
                )
            }
            // todo: more complex long ops
            else -> throw IllegalStateException("$keyOp")
        }

    /**
     * The resulting instructions will push the passed string to the stack
     */
    private fun InsnBuilder.handleString(string: String) {
        val key = Random.nextInt(Int.MIN_VALUE, Int.MAX_VALUE)
        val node = classPool.getNext()

        val k0 = Random.nextLong()
        val k1 = Random.nextLong()
        val k2 = Random.nextLong()
        val k3 = Random.nextLong()

        newObject(node.name, initDesc) {
            if (!keyPool) return@newObject
            loadConstant(k0)
            loadConstant(k1)
            loadConstant(k2)
            loadConstant(k3)
        }

        val keyOp = if (moreKeyOps) (node.metadata[1] as Pair<Int, Int>).second
                    else 0

        val k by lazy { node.fields[0].value as Long }
        val pool0 by lazy { node.fields[1].value as Long }
        val pool1 by lazy { node.fields[3].value as Long }
        val pool2 by lazy { node.fields[5].value as Long }
        val pool3 by lazy { node.fields[7].value as Long }

        val l0 by lazy { (pool0 xor k0) and (k3 xor k) }
        val l1 by lazy { (pool1 xor k1) and (k2 xor k) }
        val l2 by lazy { (pool2 xor k2) and (k1 xor k) }
        val l3 by lazy { (pool3 xor k3) and (k0 xor k) }

        val builder = StringBuilder()
        val nullBytes = (8 - (string.length % 8)) % 8

        for ((index, block) in getInBlocks(string).withIndex()) {
            var final = bytesToLong(block)

            if (keyPool) {
                val poolValue = when (index % 5) {
                    0 -> l0
                    1 -> l1
                    2 -> l2
                    3, 4 -> l3
                    else -> throw IllegalStateException()
                }

                final = doKeyOp(poolValue, final, keyOp)
            }

            builder.append(final).append(';')
        }
        
        loadConstant(xor(builder.toString(), key, node))
        loadConstant(key)
        loadConstant(nullBytes)
        invokeVirtual(node.name, "decrypt", decryptDesc)
    }

    private fun getInBlocks(string: String, blockSize: Int = 8) =
        string.encodeToByteArray()
            .asList()
            .chunked(blockSize) { chunk ->
                ByteArray(blockSize).also { dst ->
                    for (i in chunk.indices) dst[i] = chunk[i]
                }
            }

    private fun xor(string: String, key: Int, node: CClassNode): String {
        val chars = string.toCharArray()
        var dKey = if (extraKey) ((node.metadata[0] as Int xor string.length) and 0x7FFFFFFF) or 1
                   else 0
        val keyOp = if (moreKeyOps) (node.metadata[1] as Pair<Int, Int>).first
                    else 0

        for ((index, ch) in chars.withIndex()) {
            var value = doKeyOp(ch.code, key + index, keyOp)

            if (value.toChar() == '\u0000') {
                throw IllegalStateException("(PRE) $ch, $dKey, $keyOp")
            }

            if (extraKey) {
                value = doKeyOp(value, dKey, keyOp)
                dKey = (dKey xor index) - key
            }

            chars[index] = value.toChar()
            if (value.toChar() == '\u0000') {
                throw IllegalStateException("$ch, $dKey, $keyOp")
            }
        }

        return String(chars)
    }

    private fun doKeyOp(a: Int, b: Int, keyOp: Int): Int =
        when (keyOp) {
            0 -> a xor b
            1 -> {
                val v = a and 0xFFFF
                val b = b and 15
                ((v shl b) or (v ushr (16 - b))) and 0xFFFF
            }
            else -> throw IllegalStateException()
        }

    private fun doKeyOp(a: Long, b: Long, keyOp: Int): Long =
        when (keyOp) {
            0 -> a xor b
            1 -> b.rotateLeft(a.toInt())
            else -> throw IllegalStateException()
        }

    fun bytesToLong(b: ByteArray): Long {
        var result: Long = 0
        for (i in 0..<Long.SIZE_BYTES) {
            result = result shl Byte.SIZE_BITS
            result = result or (b[i].toInt() and 0xFF).toLong()
        }
        return result
    }

}