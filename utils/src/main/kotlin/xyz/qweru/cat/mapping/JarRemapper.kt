package xyz.qweru.cat.mapping

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.JarContainer
import xyz.qweru.cat.mapping.klass.LocalFieldLookupRemapper
import xyz.qweru.cat.mapping.klass.LookupRemapper
import xyz.qweru.cat.mapping.resource.ResourceRemapper
import xyz.qweru.cat.mapping.resource.meta.ManifestRemapper
import xyz.qweru.cat.profile.Timer
import xyz.qweru.cat.thread.Threads

object JarRemapper {
    private val logger = KotlinLogging.logger {}

    fun remap(container: JarContainer, config: Configuration) = container.apply {
        val timer = Timer()
        val remapper = LookupRemapper(mappings)
        val parallel = Threads.optional(config.threadRemap)
        { fromCount(classes.size + 1, config.threadRemapCapacity) }
            .createInvocator()

        // remap classes

        for (entry in classes) {
            parallel {
                val initial = entry.value
                val result = ClassNode()
                initial.accept(ClassRemapper(result, remapper))
                entry.setValue(result)

                val methods = arrayListOf<MethodNode>()
                for (i in 0..<result.methods.size) {
                    val method = result.methods[i]
                    val remappedNode = MethodNode()

                    remappedNode.access = method.access
                    remappedNode.name = method.name
                    remappedNode.desc = method.desc
                    remappedNode.signature = method.signature
                    remappedNode.exceptions = method.exceptions

                    method.accept(LocalFieldLookupRemapper(remapper, initial.name, initial.methods[i].name, remappedNode))
                    methods.add(remappedNode)
                }

                result.methods = methods
            }
        }

        // remap resources

        for (resource in resources) {
            parallel {
                val name = resource.name
                val remapper: ResourceRemapper = when {
                    name == "META-INF/MANIFEST.MF" -> ManifestRemapper
                    else -> return@parallel
                }

                remapper.remap(resource, mappings, config)
            }
        }

        parallel.await()
        logger.info { "Remapping took ${timer.time()}ms" }
    }
}