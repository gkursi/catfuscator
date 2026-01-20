package xyz.qweru.cat.transform.flow

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.instructionsFor
import xyz.qweru.cat.util.asm.isStatic
import xyz.qweru.cat.util.asm.transformClass
import xyz.qweru.cat.util.asm.transformMethod
import xyz.qweru.cat.util.generate.MaxLoadPool
import xyz.qweru.cat.util.generate.findFields
import xyz.qweru.cat.util.generate.pickRandom
import xyz.qweru.cat.util.generate.sortedRandomInts
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

class GotoSwitchTransformer(
    target: JarContainer,
    opts: Configuration
) : Transformer("GotoSwitch", "Replace goto opcodes with switch statements (unstable)", target, opts) {
    val heavy by value("Fake Cases", "Creates fake cases", true)
    val heavyCount by value("Case Count", "Fake case count", 10)
    val heavyVarUsage by value("Max Var Usage", "Max var usage in fake cases", 5)
    val scopeCrasher by value("Scope Crasher", "Crashes most decompilers with funny jumps", false)

    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) continue

                val klass = entry.value
                val staticFields = findFields(klass, "I")
                val nonStaticFields = findFields(klass, "I", static = false)

                val intPool = MaxLoadPool(heavyVarUsage) {
                    val fieldName = "${it}gotoSwitch$${klass.name.replace("/", "_")}"
                    transformClass(klass) {field(
                        fieldName,
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                        "I",
                        Random.nextInt(Int.MIN_VALUE, 0)
                    )}
                    return@MaxLoadPool fieldName
                }

                for (method in klass.methods) {
                    transformMethod(method) {
                        var labels = arrayListOf<LabelNode>()
                        replace({ it.opcode == Opcodes.GOTO }) { goto, _, _ ->
                            goto as JumpInsnNode

                            if (!scopeCrasher) {
                                labels = arrayListOf()
                            }

                            instructionsFor(method) {
                                val _this = klass.name

                                if (heavy) {
                                    getStaticField(_this, intPool.getNext(), "I")
                                } else {
                                    loadConstant(Random.nextInt(Int.MAX_VALUE))
                                }

                                lookupSwitchBuilder {
                                    defaultCase {
                                        // todo
//                                        pickRandom(
//                                            {
//                                                getStaticField(_this, intPool.getNext(), "I")
//                                                getStaticField(_this, intPool.getNext(), "I")
//                                            }
//                                        )
                                        jump(goto.label)
                                    }

                                    if (!heavy) {
                                        return@lookupSwitchBuilder
                                    }

                                    val ints = sortedRandomInts(Int.MAX_VALUE, heavyCount)
                                    for (i in 0..<heavyCount) {
                                        case(ints[i]) { end ->
                                            val field = randomField(intPool, staticFields)
                                            // todo:  Split these up in to smaller fragments
                                            // todo:  for more randomness to reduce patterns
                                            // todo:  or add more (or both).
                                            pickRandom(
                                                {
                                                    +label().also { labels.add(it) }
                                                    getStaticField(_this, field, "I")
                                                    ldc(Random.nextInt())
                                                    addInts()
                                                },
                                                {
                                                    +label().also { labels.add(it) }
                                                    ldc(Random.nextLong())
                                                    getStaticField(_this, field, "I")
                                                    int2Long()
                                                    compareLongs()
                                                },
                                                {
                                                    +label().also { labels.add(it) }
                                                    if (method.isStatic || nonStaticFields.isEmpty()) {
                                                        ldc(Random.nextInt())
                                                    } else {
                                                        loadLocalObject(0)
                                                        getField(_this, nonStaticFields.random(), "I")
                                                    }
                                                }
                                            )
                                            // stack: .., integer
                                            pickRandom(
                                                {
                                                    ldc(Random.nextInt(Int.MIN_VALUE, 0))
                                                    orInts() // int
                                                    dup()
                                                    dup() // int, int, int
                                                    storeStaticField(_this, field, "I") // int, int
                                                    ldc(Random.nextInt(4, 32)) // int, int, int
                                                    newByteArray() // int, int, [B
                                                    dup() // int, int, [B, [B
                                                    dup2_x2()
                                                    pop2() // [B, [B, int, int
                                                    ldc(Random.nextInt())
                                                    xorInts()
                                                    int2Byte()
                                                    storeByteInArray()
                                                },
                                                {
                                                    val skip1 = label()
                                                    getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    xorInts() // int
                                                    if (method.isStatic || nonStaticFields.isEmpty()) {
                                                        getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    } else {
                                                        loadLocalObject(0)
                                                        getField(_this, nonStaticFields.random(), "I")
                                                    }
                                                    andInts() // int
                                                    constant0() // int 0
                                                    jumpIfIntSmaller(skip1)
                                                    +label().also { labels.add(it) }
                                                    if (method.isStatic || nonStaticFields.isEmpty()) {
                                                        getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    } else {
                                                        loadLocalObject(0)
                                                        getField(_this, nonStaticFields.random(), "I")
                                                    }
                                                    getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    mulInts()
                                                    if (method.isStatic || nonStaticFields.isEmpty() || Random.nextBoolean()) {
                                                        storeStaticField(_this, randomField(intPool, staticFields), "I")
                                                    } else {
                                                        loadLocalObject(0)
                                                        storeField(_this, nonStaticFields.random(), "I")
                                                    }
                                                    jump(if (Random.nextBoolean()) goto.label else labels.random())
                                                    +skip1
                                                    +label().also { labels.add(it) }
                                                    if (method.isStatic || nonStaticFields.isEmpty()) {
                                                        getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    } else {
                                                        loadLocalObject(0)
                                                        getField(_this, nonStaticFields.random(), "I")
                                                    }
                                                    newByteArray()
                                                    dup()
                                                    constant0()
                                                    if (method.isStatic || nonStaticFields.isEmpty()) {
                                                        getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    } else {
                                                        loadLocalObject(0)
                                                        getField(_this, nonStaticFields.random(), "I")
                                                    }
                                                    int2Byte()
                                                    storeByteInArray()
                                                }
                                            )
                                            // stack: .., bytearray
                                            pickRandom(
                                                {
                                                    val skip = label()
                                                    dup()
                                                    constant0()
                                                    loadByteFromArray()
                                                    jumpIfLessThan(skip)
                                                    ldc(Random.nextInt(10000))
                                                    loadByteFromArray()
                                                    if (method.isStatic || nonStaticFields.isEmpty() || Random.nextBoolean()) {
                                                        storeStaticField(_this, randomField(intPool, staticFields), "I")
                                                    } else {
                                                        loadLocalObject(0)
                                                        storeField(_this, nonStaticFields.random(), "I")
                                                    }
                                                    jump(if (Random.nextBoolean()) goto.label else labels.random())
                                                    +skip
                                                },
                                                {}
                                            )
                                            // stack: .., bytearray
                                            pickRandom(
                                                {
                                                    dup()
                                                    constant0()
                                                    loadByteFromArray()
                                                    getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    andInts()
                                                    swap()
                                                    dup()
                                                    arrayLength()
                                                    constant1()
                                                    subInts()
                                                    loadByteFromArray()
                                                    ldc(255)
                                                    andInts()
                                                    xorInts()
                                                    dup()
                                                    getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    addInts()
                                                    getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    xorInts()
                                                    ldc(7)
                                                    addInts()
                                                    storeStaticField(_this, randomField(intPool, staticFields), "I")
                                                    pop()
                                                },
                                                {
                                                    dup()
                                                    arrayLength()
                                                    constant5()
                                                    mulInts()
                                                    swap()
                                                    dup()
                                                    constant0()
                                                    loadByteFromArray()
                                                    ldc(255)
                                                    andInts()
                                                    swap()
                                                    dup()
                                                    getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    loadByteFromArray()
                                                    ldc(255)
                                                    andInts()
                                                    ldc(8)
                                                    shlInts()
                                                    swap()
                                                    pop()
                                                    orInts()
                                                    addInts()
                                                    getStaticField(_this, randomField(intPool, staticFields), "I")
                                                    xorInts()
                                                    storeStaticField(_this, randomField(intPool, staticFields), "I")
                                                }
                                            )
                                            jump(end)
                                        }
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

    private fun randomField(pool: MaxLoadPool<String>, fields: List<String>): String =
        if (Random.nextBoolean() || fields.isEmpty()) pool.getNext() else fields.random()
}