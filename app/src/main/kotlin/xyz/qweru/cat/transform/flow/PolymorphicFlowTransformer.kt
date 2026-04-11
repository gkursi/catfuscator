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
import xyz.qweru.cat.util.analysis.rebuildFromGraph
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

    val propagateLoops by value("Expand Loops", "Expands looping graph edges", true)
    val randomProp by value("Connect Expansion", "Allow connections from expansions back to the originals", true)

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
                    parallel {
                        logger.info { " - ${method.name}" }
                        val flow = analyzeCfg(
                            method.instructions,
                            loops = true
                        )

                        if (propagateLoops) {
                            propagateLoops(flow.entrypoint)
                        }

                        method.instructions.rebuildFromGraph(flow.entrypoint)
                    }
                }
            }
        }

        parallel.await()
    }

    private fun Block.randomClone(): Block =
        if (randomProp && Random.nextBoolean()) {
            this
        } else {
            this.clone()
        }

    private fun propagateLoops(block: Block) = block.forEachEdge { edge, target ->
        if (edge !is Edge.Jump || !edge.loop) {
            return@forEachEdge
        }

        val endpoints = target.endpoints
            .associate {
                if (it !is Edge.Jump && it !is Edge.Switch) {
                    return@associate it to it
                }

                return@associate when (it) {
                    is Edge.Switch -> it to Edge.Switch(
                        it.parent,
                        it.keys,
                        it.values.map { jp ->
                            Edge.Jump(
                                target,
                                jp.target.randomClone(),
                                jp.op,
                                jp.loop
                            )
                        }.toMutableList(),
                        it.op
                    )

                    is Edge.Jump -> it to Edge.Jump(
                        target,
                        it.target.randomClone(),
                        it.op,
                        it.loop
                    )

                    else -> throw IllegalStateException()
                }
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
}