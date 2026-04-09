package xyz.qweru.cat.util.analysis

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import xyz.qweru.cat.util.asm.cloneExact
import xyz.qweru.cat.util.asm.cloneExactExcept
import xyz.qweru.cat.util.collection.LiteralMap

/**
* Constructs a graph from an instruction list.
*/
fun analyzeCfg(insns: InsnList): FlowAnalysis {
    val blocksByLabel = Object2ObjectOpenHashMap<LabelNode, Block>()

    val entry = createFlow(
        insns,
        blocksByLabel,
        insns.first as LabelNode,
        0
    )

    entry.entrypoints.add(Edge.MethodEntry)
    analyzeEdge(entry)

    return FlowAnalysis(
        blocksByLabel,
        entry
    )
}

/**
 * Recursively analyze all edges reachable from the given block
 */
fun analyzeEdge(entry: Block) = entry.forEachEdge { next, target ->
    if (next !is Edge.Jump) {
        return@forEachEdge
    }

    next.loop = target.canLeadTo(next.parent)
}

/**
 * Creates a basic graph
 */
fun createFlow(
    insns: InsnList,
    blocksByLabel: MutableMap<LabelNode, Block>,
    blockLabel: LabelNode,
    id: Int
): Block {
    var node: AbstractInsnNode = blockLabel
    val nextId = id + 1

    if (blocksByLabel.containsKey(blockLabel)) {
        throw IllegalStateException("Visited the same block twice")
    }

    val block = Block(blockLabel).also {
        it.flowId = id
        blocksByLabel[blockLabel] = it
    }

    fun process(fork: LabelNode, op: Int, switch: Boolean): Edge.Jump {
        var edge: Edge.Jump

        if (blocksByLabel.contains(fork)) {
            val target = blocksByLabel[fork]!!

            edge = Edge.Jump(
                block,
                target,
                op
            )

            target.entrypoints.add(edge)
        } else {
            val target = createFlow(
                insns,
                blocksByLabel,
                fork,
                nextId
            )

            edge = Edge.Jump(
                block,
                target,
                op
            )

            target.entrypoints.add(edge)
        }

        if (!switch) {
            block.endpoints.add(edge)
            block.instructions.add(edge)
        }

        return edge
    }

    while (true) {
        if (node is LabelNode && node != blockLabel) {
            val target = blocksByLabel[node]
                ?: createFlow(
                    insns,
                    blocksByLabel,
                    node,
                    nextId
                )

            val jmp = Edge.Fallthrough(block, target)

            block.endpoints.add(jmp)
            block.instructions.add(jmp)
            target.entrypoints.add(jmp)

            break
        }

        if (node.isFork) {
            when (node.opcode) {
                in Opcodes.IFEQ..Opcodes.GOTO -> {
                    val label = (node as JumpInsnNode).label
                    process(label, node.opcode, false)
                }

                Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH -> {
                    val jumps: MutableList<Edge.Jump> = arrayListOf()
                    val labels: List<LabelNode>
                    val keys: MutableList<Int>

                    when (node) {
                        is TableSwitchInsnNode -> {
                            keys = (node.min..node.max).toMutableList()
                            labels = node.labels + node.dflt
                        }

                        is LookupSwitchInsnNode -> {
                            keys = node.keys.toMutableList()
                            labels = node.labels + node.dflt
                        }

                        else -> throw IllegalStateException("Invalid opcode")
                    }

                    labels.forEach {
                        val node = process(it, node.opcode, true)
                        jumps.add(node)
                    }

                    val insn = Edge.Switch(
                        block,
                        keys,
                        jumps,
                        node.opcode
                    )

                    block.instructions.add(insn)
                    block.endpoints.add(insn)
                }
            }
        } else if (node != blockLabel) { // forks are added to the instruction list by `process`
            block.instructions.add(node)
        }

        if (node.isTerminating) {
            break
        }

        node = node.next ?: break
    }

    return block
}

/**
 * Loop through all graph edges
 */
inline fun Block.forEachEdge(consume: (Edge, Block) -> Unit) {
    val remaining = ArrayDeque<Edge>()
    val visited = hashSetOf<Edge>()

    remaining.addAll(endpoints)

    while (remaining.isNotEmpty()) {
        val next = remaining.removeFirst()

        if (visited.contains(next)) {
            continue
        }

        visited.add(next)

        val target = when (next) {
            is Edge.Jump -> next.target
            is Edge.Fallthrough -> next.to
            else -> throw IllegalStateException("MethodEntry in endpoints")
        }

        remaining.addAll(target.endpoints)

        consume(next, target)
    }
}

/**
 * BFS graph search
 */
fun Block.canLeadTo(other: Block): Boolean {
    forEachEdge { _, target ->
        if (target == other) {
            return true
        }
    }

    return false
}

data class FlowAnalysis(
    val blocks: Map<LabelNode, Block>,
    val entrypoint: Block
)

sealed class Edge : AbstractInsnNode(-1) {
    override fun getType() = 7

    override fun accept(methodVisitor: MethodVisitor?) =
        throw IllegalStateException("Cannot accept abstract jump")

    override fun clone(clonedLabels: Map<LabelNode?, LabelNode?>?): AbstractInsnNode =
        throw IllegalStateException("Cannot clone abstract jump")

    /**
     * Start of the method
     */
    object MethodEntry : Edge()

    /**
     * Execution falls through blocks
     */
    data class Fallthrough(
        val from: Block,
        val to: Block
    ) : Edge()

    /**
     * Jumps to a new path segment
     */
    data class Jump(
        /**
         * The block that contains this jump
         */
        val parent: Block,

        /**
         * Target block
         */
        var target: Block,

        /**
         * Original opcode
         */
        val op: Int,

        /**
         * True if this jump is reachable from its target
         */
        var loop: Boolean = false
    ) : Edge()

    data class Switch(
        val parent: Block,
        val keys: MutableList<Int>,
        val values: MutableList<Jump>,
        val op: Int
    ) : Edge()

    // todo: separate switches
}

data class Block(
    val label: LabelNode,
    val entrypoints: MutableSet<Edge> = hashSetOf(),
    val endpoints: MutableSet<Edge> = hashSetOf(),
    // not an InsnList, because asm can't handle the same
    // insn node being in multiple InsnLists at once
    val instructions: ArrayList<AbstractInsnNode> = arrayListOf(),
) {
    /**
     * Flow generation id
     * todo: maybe use for
     */
    var flowId: Int = -1

    /**
     * Clones this block, does not create
     * a unique set for entries
     */
    fun clone(): Block = Block(
        LabelNode(Label()),
        entrypoints,
        endpoints.toHashSet(),
        instructions.cloneExactExcept(
            LiteralMap<Edge>()
        )
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Block

        return label == other.label
    }

    override fun hashCode(): Int {
        return label.hashCode()
    }
}