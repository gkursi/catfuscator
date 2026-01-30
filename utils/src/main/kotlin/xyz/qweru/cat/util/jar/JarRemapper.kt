package xyz.qweru.cat.util.jar

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.mapping.klass.LocalFieldLookupRemapper
import xyz.qweru.cat.util.mapping.klass.LookupRemapper
import xyz.qweru.cat.util.mapping.resource.ResourceRemapper
import xyz.qweru.cat.util.mapping.resource.meta.ManifestRemapper
import xyz.qweru.cat.util.profile.Timer
import xyz.qweru.cat.util.thread.Threads
import kotlin.collections.iterator

private val logger = KotlinLogging.logger {}

fun remapJar(container: JarContainer, config: Configuration) = container.apply {
    val timer = Timer()
    val remapper = LookupRemapper(mappings)
    val parallel = Threads.optional(config.threadRemap)
        { fromCount(classes.size + 1, config.threadRemapCapacity) }
        .createInvocator()

    // remap classes, methods, fields, local fields

    for (entry in classes) {
        parallel {
            val source = entry.value
            val remapped = ClassNode()

            source.accept(ClassRemapper(remapped, remapper))
            entry.setValue(remapped)

            val methods = arrayListOf<MethodNode>()
            for ((i, method) in remapped.methods.withIndex()) {
                val remappedNode = MethodNode()
                    .also(methods::add)

                remappedNode.access = method.access
                remappedNode.name = method.name
                remappedNode.desc = method.desc
                remappedNode.signature = method.signature
                remappedNode.exceptions = method.exceptions

                method.accept(
                    LocalFieldLookupRemapper(
                        remapper,
                        source.name,
                        source.methods[i].name,
                        remappedNode
                    )
                )
            }

            remapped.methods = methods
        }
    }

    // remap resources

    for (resource in resources) {
        parallel {
            val name = resource.name
            val remapper: ResourceRemapper = when {
                // todo: fabric/mixin metadata
                name == "META-INF/MANIFEST.MF" -> ManifestRemapper
                else -> return@parallel
            }

            remapper.remap(resource, mappings, config)
        }
    }

    parallel.await()
    logger.info { "Remapping took ${timer.time()}ms" }
}