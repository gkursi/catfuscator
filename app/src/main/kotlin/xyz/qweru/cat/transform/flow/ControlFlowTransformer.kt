package xyz.qweru.cat.transform.flow

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.*
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.generate.getJumpTargets
import xyz.qweru.cat.util.generate.nextNonZeroInt
import xyz.qweru.cat.util.generate.pickRandom
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

class ControlFlowTransformer : Transformer("ControlFlow", "Generic control flow obfuscation") {

//    val heavy by value("Heavy", "Heavy flow obfuscation (unstable)", false)
    var shuffle by value("Block Shuffle", "Shuffles basic blocks (unstable-ish)", false)
    var globalVT by value("Global Variable Table", "Make every local exist everywhere", false)

    var junkFlow by value("Junk Flow", "Add junk control flow", false)
    val trappedJump by value("Trapped Jump", "Also adds trapped jumps", false)
    val trappedJumpChance by value("Trap Chance", "Trapped jump chance", 0.05)
    val maxLocals by value("Max Locals", "Max amount of allowed locals (per type) for junk code", 10)
    val localModifyChance by value("Local Modify Chance", "Chance of modifying a local", 0.1)

    override fun apply(target: JarContainer, opts: Configuration) {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) {
                    continue
                }

                val klass = entry.value
                for (method in klass.methods) {
                    parallel {
                        transformMethod(method) {
//                            // Fixme
//                            if (heavy) {
//                                createBlocks(klass, method)
//                            }

                            if (junkFlow) {
                                bogusJumps(klass, method)
                            }

                            if (shuffle) {
                                val blocks = collectBlocks()
                                instructions.clear()

                                instructions.add(instructionsFor(method) {
                                    val first = blocks[0].labelNode
                                    blocks.shuffle()
                                    jump(first)

                                    for ((index, block) in blocks.withIndex()) {
                                        +block.labelNode
                                        block.insns.forEach(::instruction)
                                    }
                                })
                            }

                            if (globalVT) {
                                globalize(method)
                            }
                        }
                    }
                }
            }
        }

        parallel.await()
    }

    private fun MethodTransformer.bogusJumps(klass: ClassNode, method: MethodNode) {
        // fixme
        if (method.name == "<init>") {
            return
        }

        val frames = analyseMethodStack(method)
        val insns = method.instructions.toArray()
        val (methodStart, methodEnd) = findEdgeLabels(createPass())

        val intLocals = Array(Random.nextInt(
            1,
            maxLocals + 2
        )) {
            Field(0, Strategy.INCREASE_MOD)
        }

        val boolLocals = Array(Random.nextInt(
            1,
            maxLocals + 2
        )) {
            Field(0, Strategy.INCREASE_MOD)
        }

        val pass = createPass()

        if (maxLocals > 0) {
            val offset = method.localVariables?.sumOf {
                if (it.desc == "J" || it.desc == "D") {
                    2
                } else {
                    1
                }
            } ?: 0

            pass.insertHead(instructionsFor(method) {
                variableIndex += offset

                for ((index, _) in intLocals.withIndex()) {
                    val local = local("0", "I", methodStart, methodEnd)
                    val strat = Strategy.entries.random()
                    val field = Field(local, strat)

                    var value = if (Random.nextBoolean()) {
                        Random.nextInt(-100, 100)
                    } else {
                        Random.nextInt()
                    }

                    when (strat) {
                        Strategy.INCREASE_MOD -> {
                            field.data = Random.nextInt(1, Int.MAX_VALUE)
                            value %= field.data
                        }

                        Strategy.CONSTANT -> {
                            field.data = value
                        }
                    }

                    ldc(value)
                    storeLocalInt(local)
                    intLocals[index] = field
                }

                for ((index, _) in boolLocals.withIndex()) {
                    val local = local("1", "Z", methodStart, methodEnd)
                    val strategy = Strategy.entries.random()
                    val field = Field(local, strategy)

                    field.data = if (Random.nextBoolean()) 1 else 0
                    ldc(field.data)
                    storeLocalInt(local)

                    boolLocals[index] = field
                }
            })

            pass.insertBefore({ Random.nextDouble() < localModifyChance }) { _, _, _ ->
                instructionsFor(method) {
                    pickRandom(
                        {
                            val local = intLocals.random()
                            loadLocalInt(local.index)

                            when (local.strat) {
                                Strategy.INCREASE_MOD -> {
                                    ldc(Random.nextNonZeroInt())
                                    addInts()
                                    ldc(local.data)
                                    moduloInts()
                                }

                                Strategy.CONSTANT -> {
                                    dup()
                                    constant2()
                                    mulInts()
                                    swap()
                                    subInts()
                                    dup() // i i
                                    int2Double()
                                    invokeStatic("java/lang/Math", "signum", "(D)D") // i sign((D)i)
                                    double2Int()
                                    swap()
                                    invokeStatic("java/lang/Math", "abs", "(I)I") // sign(i) abs(i)
                                    mulInts()
                                }
                            }

                            storeLocalInt(local.index)

                        },
//                        {
//                            val local = boolLocals.random()
//                        }
                    )
                }
            }
        }

        pass.insertBefore({ true }) { _, _, i ->
            val frame = frames[i]

            if (frame == -1L) {
                return@insertBefore null
            }

            val targets = getJumpTargets(
                frame,
                method,
                frames,
                insns
            )

            if (targets.isEmpty()) {
                return@insertBefore null
            }

            instructionsFor(method) {
                val target = if (trappedJump && Random.nextDouble() < trappedJumpChance) {
                    val label = label()
                    +label
                    label
                } else {
                    targets.random()
                }

                if (maxLocals <= 0) {
                    constantNull()
                    jumpIfNonNull(target)
                    return@instructionsFor
                }

                pickRandom(
                    {
                        val local = intLocals.random()
                        loadLocalInt(local.index)

                        when (local.strat) {
                            Strategy.CONSTANT -> {
                                pickRandom(
                                    {
                                        if (local.data < 0) {
                                            ldc(Random.nextInt(Int.MAX_VALUE))
                                            jumpIfIntGreaterEq(target)
                                        } else {
                                            ldc(Random.nextInt(Int.MIN_VALUE, 0))
                                            jumpIfIntSmaller(target)
                                        }
                                    },
                                    {
                                        val key = Random.nextInt()

                                        pickRandom(
                                            {
                                                ldc(local.data - key)
                                                ldc(key)
                                                addInts()
                                            },
                                            {
                                                ldc(local.data + key)
                                                ldc(key)
                                                subInts()
                                            },
                                            {
                                                ldc(local.data xor key)
                                                ldc(key)
                                                xorInts()
                                            }
                                        )

                                        pickRandom(
                                            { jumpIfIntSmaller(target) },
                                            { jumpIfIntGreater(target) },
                                            { jumpIfIntNotEqual(target) },
                                        )
                                    }
                                )
                            }

                            Strategy.INCREASE_MOD -> {
                                invokeStatic("java/lang/Math", "abs", "(I)I")
                                pickRandom(
                                    {
                                        ldc(Random.nextInt(local.data + 1, Int.MAX_VALUE))
                                        jumpIfIntGreaterEq(target)
                                    },
                                    {
                                        negInt()
                                        ldc(Random.nextInt(Int.MIN_VALUE, -local.data))
                                        jumpIfIntSmaller(target)
                                    }
                                )
                            }
                        }
                    },
                    {
                        val local = boolLocals.random()
                        loadLocalInt(local.index)
                        if (local.data == 0) {
                            jumpIfNotEqual(target)
                        } else {
                            jumpIfEquals(target)
                        }
                    }
                )
            }
        }
    }

    private fun MethodTransformer.globalize(method: MethodNode) {
        val pass = createPass()
        val (startLabel, endLabel) = findEdgeLabels(pass)

        var index = if (method.isStatic) 0 else 1
        val locals = method.localVariables
            .sortedWith(
                Comparator.comparingInt(LocalVariableNode::index)
            )

        for (lv in locals) {
            if (!method.isStatic && lv.index == 0) {
                continue
            }

            val from = instructions.indexOf(lv.start)

            var foundEnd = false
            val to = instructions.indexOfFirst { node ->
                if (node !is LabelNode) {
                    return@indexOfFirst false
                }

                if (foundEnd) {
                    return@indexOfFirst true
                } else if (node == lv.end) {
                    foundEnd = true
                }

                return@indexOfFirst false
            }

            pass.find({ it is VarInsnNode }) { lvn, _, i ->
                lvn as VarInsnNode
                if (i !in from..to || lvn.`var` != lv.index) {
                    return@find
                }

                lvn.`var` = index
            }.find({ it is IincInsnNode }) { lvn, _, i ->
                lvn as IincInsnNode
                if (i !in from..to || lvn.`var` != lv.index) {
                    return@find
                }

                lvn.`var` = index
            }

            lv.start = startLabel
            lv.end = endLabel
            index += if (lv.desc == "J" || lv.desc == "D") 2 else 1
        }
    }

    // fixme
//    private fun MethodTransformer.createBlocks(klass: ClassNode, method: MethodNode) {
//        println("method ${method.name}")
//        val frames = analyseMethod(klass, method)
//        createPass().insertBeforeIndexed({ _, i ->
//            frames[i]?.stackSize == 0
//        }) { _, _, _ ->
//            InsnList().apply { add(LabelNode(Label())) }
//        }
//    }

    private fun MethodTransformer.collectBlocks(): ArrayList<Block> {
        val blocks = arrayListOf<Block>()
        var block: Block? = null
        for (node in instructions) {
            when (node) {
                is LabelNode -> {
                    if (block != null) {
                        block.insns.add(JumpInsnNode(
                            Opcodes.GOTO,
                            node
                        ))

                        blocks.add(block)
                    }

                    block = Block(node, arrayListOf())
                }

                else -> block!!.insns.add(node)
            }
        }

        block?.let { blocks.add(it) }
        return blocks
    }

    private data class Block(val labelNode: LabelNode, val insns: ArrayList<AbstractInsnNode>)

    private data class Field(val index: Int, val strat: Strategy, var data: Int = 0)

    private enum class Strategy {
        // data contains the max abs value
        INCREASE_MOD,
        // data contains the value
        CONSTANT
    }
}