package xyz.qweru.cat.transform.flow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Frame
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.asm.*
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.generate.nextNonZeroInt
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

private val logger = KotlinLogging.logger {  }

/**
 * todo: fix (unreachable?) jumps
 *       fix locals
 *       implement switches
 */
class PolymorphicFlowTransformer(
    target: JarContainer, opts: Configuration
) : Transformer("PolymorphicFlow", "Polymorphic flow obfuscation (unstable)", target, opts) {

    init {
        val parallel = createExecutorFrom(opts)
        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) {
                    continue
                }

                val klass = entry.value
                val _this = klass.name

                for (method in klass.methods) {
                    val fieldA = "polyA$${method.name}"
                    val fieldB = "polyB$${method.name}"

                    val valuesA = IntArray(3)
                    val valuesB = IntArray(3)

                    valuesA[0] = Random.nextNonZeroInt()
                    valuesB[0] = Random.nextNonZeroInt() xor valuesA[0]

                    valuesA[1] = valuesA[0] xor valuesB[0]
                    valuesB[1] = valuesB[0] xor valuesA[1]

                    valuesA[2] = valuesA[1] xor valuesB[1]
                    valuesB[2] = valuesB[1] xor valuesA[2]

                    transformClass(klass) {
                        field(fieldA, PUBLIC_STATIC, "I", valuesA[0])
                        field(fieldB, PUBLIC_STATIC, "I", valuesA[1])
                    }

                    parallel {
                        transformMethod(method) {
                            val (blocks, blocksByLabel) = collectBlocks(klass, method)
                            val mutBlocks = arrayListOf<Block>()

                            for ((i, block) in blocks
                                .withIndex()
                            ) {
                                repeat(3) {
                                    val mut = Block(LabelNode(Label()))

                                    block.variations.add(mut)
                                    mutBlocks.add(mut)
                                }
                            }

                            for ((i, block) in blocks[0].variations
                                .withIndex()
                            ) {
                                block.path = i
                                block.key = valuesA[i]
                            }

                            for (block in blocks) {
                                for ((i, mut) in block.variations
                                    .shuffled()
                                    .withIndex()
                                ) {
                                    for (insn in block.insns) {
                                        if (insn is JumpVariantNode) {
                                            mut.insns.add(insn.clone(mut))
                                            continue
                                        }

                                        mut.insns.add(insn.clone(null))
                                    }

                                    val rng = mut.key xor Random.nextNonZeroInt()
                                    val key = rng xor when (i % 3) {
                                        0 -> {
                                            mut.insns.addAll(instructionsFor(method) {
                                                ldc(rng)
                                                getStaticField(_this, fieldA, "I")
                                                xorInts()
                                            })

                                            valuesA[mut.path]
                                        }

                                        1 -> {
                                            mut.insns.addAll(instructionsFor(method) {
                                                ldc(rng)
                                                getStaticField(_this, fieldB, "I")
                                                xorInts()
                                            })

                                            valuesB[mut.path]
                                        }

                                        2 -> {
                                            mut.insns.addAll(instructionsFor(method) {
                                                ldc(rng)
                                                getStaticField(_this, fieldA, "I")
                                                xorInts()
                                                getStaticField(_this, fieldB, "I")
                                                xorInts()
                                            })

                                            valuesA[mut.path] xor valuesB[mut.path]
                                        }

                                        else -> throw IllegalStateException()
                                    }

                                    mut.frame = block.frame
                                    mut.next = block.next
                                        ?.variations
                                        ?.getVariant(mut.path, key)
                                }
                            }

                            val groups = hashMapOf<FrameState, ControlNode>()

                            instructions.clear()
                            instructions.add(instructionsFor(method) {
                                for (block in mutBlocks
                                    .shuffled()
                                ) {
                                    +block.labelNode
                                    block.insns.forEach(::instruction)

                                    if (block.next == null) {
                                        continue
                                    }

                                    val key = nop()
                                    val group = groups.computeIfAbsent(block.frame) { ControlNode() }

                                    group.add(key, block.next!!)
                                    jump(group.label)
                                }
                            })

                            val pass = createPass()
                            val post = LabelNode(Label())

                            pass.insertHead(instructionsFor(method) {
                                +post
                                getStaticField(_this, fieldA, "I")
                                getStaticField(_this, fieldB, "I")
                                xorInts()
                                storeStaticField(_this, fieldA, "I")

                                getStaticField(_this, fieldB, "I")
                                getStaticField(_this, fieldA, "I")
                                xorInts()
                                storeStaticField(_this, fieldB, "I")

                                // entrypoint switch
                                // todo: use an existing switch if possible

                                getStaticField(_this, fieldA, "I")
                                lookupSwitch {
                                    val entrypoints = blocks[0].variations
                                    // if one path isn't set, we can assume all of them aren't
                                    val unset = entrypoints.any { it.path == -1 }

                                    if (unset) {
                                        val values = valuesA.sorted()

                                        for ((i, block) in entrypoints
                                            .shuffled()
                                            .withIndex()
                                        ) {
                                            // this should be impossible
                                            if (block.path != -1) {
                                                throw IllegalStateException("init block has path")
                                            }

                                            block.path = i
                                            block.key = values[i]

                                            if (i == 0) {
                                                defaultCase(block.labelNode)
                                            } else {
                                                case(block.key, block.labelNode)
                                            }
                                        }
                                    } else {
                                        for ((i, block) in entrypoints
                                            .sortedWith(Comparator.comparingInt { it.key })
                                            .withIndex()
                                        ) {
                                            if (block.path == -1) {
                                                throw IllegalStateException("init block doesnt have path")
                                            }

                                            if (i == 0) {
                                                defaultCase(block.labelNode)
                                            } else {
                                                case(block.key, block.labelNode)
                                            }
                                        }
                                    }
                                }
                            })

                            pass.replace({ it is JumpVariantNode }) { insn, _, _ ->
                                instructionsFor(method) {
                                    insn as JumpVariantNode

                                    val op = insn.op
                                    val currentBlock = insn.block
                                    val targetBlock = blocksByLabel[insn.label]
                                        ?: throw IllegalStateException("Cannot resolve target block for label")

                                    val path = currentBlock.path
                                    val variant = with(targetBlock.variations) {
                                        firstOrNull { it.path == path }
                                            ?: firstOrNull { it.path == -1 }
                                            ?: throw IllegalStateException("Path $path not in ${targetBlock.variations.joinToString { it.path.toString() }}")
                                    }

                                    val group = groups.computeIfAbsent(variant.frame) {
                                        ControlNode()
                                    }

                                    var field: String
                                    var value: Int
                                    if (variant.path == -1) {
                                        value = valuesB[path]
                                        field = fieldB

                                        variant.path = path
                                        variant.key = Random.nextNonZeroInt() xor value
                                        group.add(nop(), variant)
                                    } else {
                                        field = fieldA
                                        value = valuesA[path]
                                    }

                                    ldc(variant.key xor value)
                                    getStaticField(_this, field, "I")
                                    xorInts()

                                    if (op != Opcodes.GOTO) {
                                        when (op) {
                                            Opcodes.IFNE, Opcodes.IFEQ,
                                            Opcodes.IFGE, Opcodes.IFLE,
                                            Opcodes.IFGT, Opcodes.IFLT -> dup_x1()

                                            Opcodes.IF_ICMPGE, Opcodes.IF_ICMPLE,
                                            Opcodes.IF_ICMPNE, Opcodes.IF_ICMPEQ,
                                            Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLT,
                                            Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> dup_x2()
                                        }

                                        pop()
                                    }

                                    instruction(JumpInsnNode(op, group.label))

                                    if (op != Opcodes.GOTO) {
                                        pop()
                                    }
                                }
                            }

                            pass.insertHead(instructionsFor(method) {
                                jump(post)
                                for (node in groups.values) {
                                    +node.label
                                    lookupSwitch {
                                        node.entries.sortWith(Comparator.comparingInt { it.block.key })

                                        for ((i, entry) in node.entries.withIndex()) {
                                            if (i == 0) {
                                                defaultCase(entry.block.labelNode)
                                            } else {
                                                case(entry.block.key, entry.block.labelNode)
                                            }
                                        }
                                    }
                                }
                            })

                            logger.info { "${method.name} : Created ${mutBlocks.size} blocks from ${blocks.size} blocks" }
                        }
                    }
                }
            }
        }

        parallel.await()
    }

    private fun MethodTransformer.collectBlocks(klass: ClassNode, method: MethodNode): Pair<ArrayList<Block>, Map<LabelNode, Block>> {
        val frames = analyseMethod(klass, method)
        val blocksByLabel = hashMapOf<LabelNode, Block>()
        val blocks = arrayListOf<Block>()
        var block: Block? = null

        for ((index, node) in instructions
            .withIndex()
        ) {
            when (node) {
                is LabelNode -> {
                    val previous = block
                    val frame = frames[index] ?: continue

                    if (block != null) {
                        blocks.add(block)
                        blocksByLabel[block.labelNode] = block
                    }

                    block = Block(node)
                    previous?.next = block
                    block.frame = FrameState.of(frame)
                }

                is JumpInsnNode -> {
                    block!!.insns.add(JumpVariantNode(
                        block,
                        node.label,
                        node.opcode
                    ))
                }

                else -> block!!.insns.add(node)
            }
        }

        block?.let {
            blocks.add(it)
            blocksByLabel[it.labelNode] = it
        }

        return blocks to blocksByLabel
    }

    private data class Block(
        val labelNode: LabelNode = LabelNode(Label()),
    ) {
        val insns: ArrayList<AbstractInsnNode> = arrayListOf()
        val variations by lazy { arrayListOf<Block>() }

        lateinit var frame: FrameState
        var next: Block? = null
        var path = -1

        /**
         * assume this is not set when `path == -1`
         */
        var key = 0
    }

    private class JumpVariantNode(val block: Block, val label: LabelNode, val op: Int) : AbstractInsnNode(op) {

        override fun getType(): Int = -1

        override fun accept(methodVisitor: MethodVisitor) =
            TODO()

        override fun clone(clonedLabels: Map<LabelNode?, LabelNode?>?) =
            TODO()

        fun clone(block: Block) =
            JumpVariantNode(block, label, op)

    }

    private class BlockMapWrapper(val map: Map<LabelNode, Block>) : Map<LabelNode, LabelNode> {
        override val size: Int
            get() = throw NotImplementedError("Not implemented")
        override val keys: Set<LabelNode>
            get() = throw NotImplementedError("Not implemented")
        override val values: Collection<LabelNode>
            get() = throw NotImplementedError("Not implemented")
        override val entries: Set<Map.Entry<LabelNode, LabelNode>>
            get() = throw NotImplementedError("Not implemented")

        override fun isEmpty() =
            throw NotImplementedError("Not implemented")

        override fun containsKey(key: LabelNode) =
            throw NotImplementedError("Not implemented")

        override fun containsValue(value: LabelNode) =
            throw NotImplementedError("Not implemented")

        override fun get(key: LabelNode): LabelNode? =
            map[key]?.variations?.random()?.labelNode

    }

    private class ControlNode {
        val label = LabelNode(Label())
        val entries = arrayListOf<Entry>()

        fun add(key: AbstractInsnNode, block: Block) =
            entries.add(Entry(key, block))

        data class Entry(val key: AbstractInsnNode, val block: Block)
    }

    private fun MutableCollection<Block>.getVariant(path: Int, key: Int): Block =
        first { it.path == -1 }
            .also {
                if (it.key != 0) {
                    logger.warn { "non null key!!" }
                }

                it.path = path
                it.key = key
            }
}