package xyz.qweru.cat.transform.flow

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import xyz.qweru.cat.transform.Transformer
import xyz.qweru.cat.util.analysis.Block
import xyz.qweru.cat.util.analysis.Edge
import xyz.qweru.cat.util.analysis.analyzeCfg
import xyz.qweru.cat.util.analysis.forEachEdge
import xyz.qweru.cat.util.analysis.rebuildFromGraph
import xyz.qweru.cat.util.asm.*
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.generate.sortedRandomInts
import xyz.qweru.cat.util.jar.JarContainer
import xyz.qweru.cat.util.thread.createExecutorFrom
import kotlin.random.Random

class ControlFlowFlattenTransformer : Transformer("ControlFlowFlatten", "Flatten control flow graphs") {

    val shuffle by value("Shuffle", "Shuffle case order", true)
    val onlyEmpty by value("Only Empty", "Only create labels when the stack is empty", false)

    override fun apply(target: JarContainer, opts: Configuration) {
        val parallel = createExecutorFrom(opts)

        target.apply {
            for (entry in classes.entries) {
                if (!canTarget(entry)) {
                    continue
                }

                val klass = entry.value

                for (method in klass.methods) {
                    parallel {
                        val cfg = analyzeCfg(
                            method.instructions,
                            frames = true
                        )

                        val groups = Long2ObjectOpenHashMap<Pair<Block, MutableMap<LdcInsnNode, Block>>>()

                        // redirect all jumps to their respective dispatch blocks
                        cfg.entrypoint.forEachEdge { edge, _ ->
                            when (edge) {
                                is Edge.Fallthrough -> {
                                    val block = edge.from
                                    val key = LdcInsnNode(null)

                                    val (dispatch, map) = groups.getOrPut(edge.to.frame) {
                                        Block(LabelNode(Label())) to Object2ObjectOpenHashMap()
                                    }

                                    require(block.instructions.removeLast() is Edge.Fallthrough) // fallthrough is always last
                                    block.instructions.add(key)
                                    block.instructions.add(
                                        Edge.Jump(
                                            block,
                                            dispatch,
                                            Opcodes.GOTO
                                        )
                                    )

                                    map[key] = edge.to
                                }

                                is Edge.Jump -> {
                                    if (edge.op in Opcodes.TABLESWITCH..Opcodes.LOOKUPSWITCH) {
                                        return@forEachEdge
                                    }

                                    val block = edge.parent
                                    val key = LdcInsnNode(null)
                                    val index = block.instructions.indexOf(edge)

                                    block.instructions.add(
                                        index,
                                        key
                                    )

                                    when (edge.op) {
                                        in Opcodes.IF_ICMPEQ..Opcodes.IF_ACMPNE -> {
                                            block.instructions.add(
                                                index + 1,
                                                InsnNode(Opcodes.DUP_X2)
                                            )

                                            block.instructions.add(
                                                index + 2,
                                                InsnNode(Opcodes.POP)
                                            )
                                        }

                                        in Opcodes.IFEQ..Opcodes.IFLE, Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                                            block.instructions.add(
                                                index + 1,
                                                InsnNode(Opcodes.DUP_X1)
                                            )

                                            block.instructions.add(
                                                index + 2,
                                                InsnNode(Opcodes.POP)
                                            )
                                        }
                                    }

                                    val (dispatch, map) = groups.getOrPut(edge.target.frame) {
                                        Block(LabelNode(Label())) to Object2ObjectOpenHashMap()
                                    }

                                    map[key] = edge.target
                                    edge.target = dispatch
                                }

                                else -> {}
                            }
                        }

                        val entry = Block(
                            LabelNode(),
                            hashSetOf(Edge.MethodEntry)
                        )

                        // add jump to entrypoint

                        val (dispatch, map) = groups.getOrPut(cfg.entrypoint.frame) {
                            Block(LabelNode(Label())) to Object2ObjectOpenHashMap()
                        }

                        val key = LdcInsnNode(null)

                        val jump = Edge.Jump(
                            entry,
                            dispatch,
                            Opcodes.GOTO
                        )

                        entry.instructions.add(key)
                        entry.instructions.add(jump)
                        map[key] = cfg.entrypoint

                        for (entry in groups) {
                            // todo: predicate based dispatch
                            val (block, cases) = entry.value
                            block.instructions.addAll(
                                instructionsFor(method) {
                                    lookupSwitch {
                                        val entries = cases.entries
                                            .shuffled()
                                        val keys = sortedRandomInts(
                                            Int.MAX_VALUE,
                                            entries.size
                                        )

                                        for ((index, v) in entries.withIndex()) {
                                            val (keyIns, block) = v

                                            if (index == 0) {
                                                defaultCase(block.label)
                                            } else {
                                                val key = keys[index]
                                                keyIns.cst = key
                                                case(key, block.label)
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        method.instructions.rebuildFromGraph(entry)
                    }
                }
            }
        }

        parallel.await()
    }
}