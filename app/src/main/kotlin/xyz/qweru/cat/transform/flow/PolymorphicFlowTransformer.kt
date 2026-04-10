package xyz.qweru.cat.transform.flow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.analysis.Block
import xyz.qweru.cat.util.analysis.Edge
import xyz.qweru.cat.util.analysis.analyzeCfg
import xyz.qweru.cat.util.analysis.forEachEdge
import xyz.qweru.cat.util.asm.cloneExactExcept
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

private val logger = KotlinLogging.logger {  }

class PolymorphicFlowTransformer : Transformer(
    "PolymorphicFlow",
    "Polymorphic flow obfuscation"
) {
    override fun apply(target: JarContainer, opts: Configuration) {
        val parallel = createExecutorFrom(opts)

        target.apply {
            for (entry in classes) {
                if (!canTarget(entry)) {
                    continue
                }

                val (_, klass) = entry
                logger.info { "Parsing ${klass.name}" }
                for (method in klass.methods) {
//                    parallel {
                        logger.info { " - ${method.name}" }
                        val flow = analyzeCfg(method.instructions)

                        propagateLoops(flow.entrypoint)

                        method.instructions.clear()
                        createBlock(method.instructions, flow.entrypoint, hashSetOf(flow.entrypoint))
//                    }
                }
            }
        }

        parallel.await()
    }

    private fun propagateLoops(block: Block) =
        block.forEachEdge { edge, target ->
            if (edge !is Edge.Jump || !edge.loop) {
                return@forEachEdge
            }

            val endpoints = target.endpoints
                .associate {
                    if (it !is Edge.Jump) {
                        return@associate it to it
                    }

                    it to Edge.Jump(
                        target,
                        it.target.clone(),
                        it.op,
                        it.loop
                    )
                }

            edge.target = Block(
                LabelNode(Label()),
                target.entrypoints,
                endpoints.values.toHashSet(),
                target.instructions
                    .cloneExactExcept(endpoints)
            )
            return
        }

    private fun createBlock(
        insns: InsnList,
        block: Block,
        visited: HashSet<Block>,
        jumble: Boolean = true
    ): HashSet<Block> {
        insns.add(block.label)

        fun process(block: Block) {
            if (visited.contains(block)) {
                return
            }

            visited.add(block)
            createBlock(insns, block, visited, jumble)
        }

        for (node in block.instructions) {
            val newNode = when (node) {
                is Edge.Jump -> {
                    JumpInsnNode(node.op, node.target.label)
                }

                is Edge.Fallthrough ->
                    JumpInsnNode(Opcodes.GOTO, node.to.label)

                is Edge.Switch ->
                    if (node.op == Opcodes.TABLESWITCH) {
                        TableSwitchInsnNode(
                            node.keys.first(),
                            node.keys.last(),
                            node.values.last().target.label
                        ).also {
                            val values = node.values.toMutableList()
                            values.removeLast() // default

                            it.labels = values.map { jmp ->
                                jmp.target.label
                            }
                        }
                    } else {
                        LookupSwitchInsnNode(
                            node.values.last().target.label,
                            intArrayOf(),
                            emptyArray()
                        ).also {
                            val values = node.values.toMutableList()
                            values.removeLast() // default

                            it.keys = node.keys
                            it.labels = values.map { jmp -> jmp.target.label }
                        }
                    }

                else -> node
            }

            insns.add(newNode)
        }

        for (jump in block.endpoints) {
            when (jump) {
                is Edge.Jump -> process(jump.target)
                is Edge.Fallthrough -> process(jump.to)

                is Edge.Switch -> jump.values.forEach {
                    process(it.target)
                }

                else -> continue
            }
        }

        return visited
    }
}