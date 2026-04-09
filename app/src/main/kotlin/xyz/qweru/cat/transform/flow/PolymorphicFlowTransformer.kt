package xyz.qweru.cat.transform.flow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
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

        for (node in block.instructions) {
            val newNode = when (node) {
                is Edge.Jump -> {
                    JumpInsnNode(node.op, node.target.label)
                }
                is Edge.Fallthrough ->
                    JumpInsnNode(Opcodes.GOTO, node.to.label)
                else -> node
            }

            insns.add(newNode)
        }

        for (jump in block.endpoints) {
            val block = when (jump) {
                is Edge.Jump -> jump.target
                is Edge.Fallthrough -> jump.to

                else -> continue
            }

            if (visited.contains(block)) {
                continue
            }

            visited.add(block)
            createBlock(insns, block, visited, jumble)
        }

        return visited
    }
}