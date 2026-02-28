package xyz.qweru.cat.util.mapping.resource.meta

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.Resource
import xyz.qweru.cat.util.mapping.lookup.MappingLookup
import xyz.qweru.cat.util.mapping.resource.ResourceRemapper

object ManifestRemapper : ResourceRemapper {
    private val logger = KotlinLogging.logger {}

    override fun remap(resource: Resource, mappings: MappingLookup, config: Configuration) {
        require(resource.name == "META-INF/MANIFEST.MF")
        if (!config.remapManifest) return

        val manifest = String(resource.bytes)
        val output = StringBuilder()
        for (line in manifest.lines()) {
            when {
                line.startsWith("Main-Class: ") -> {
                    val mainClass = line
                        .substring("Main-Class: ".length)
                        .replace(".", "/")
                    val remapped = (mappings.get(mainClass) ?: mainClass)
                        .replace("/", ".")
                    output.append("Main-Class: ").append(remapped)
                }

                line.startsWith("Rsrc-Main-Class: ") -> {
                    val mainClass = line
                        .substring("Rsrc-Main-Class: ".length)
                        .replace(".", "/")
                    val remapped = (mappings.get(mainClass) ?: mainClass)
                        .replace("/", ".")
                    output.append("Rsrc-Main-Class: ").append(remapped)
                }

                else -> {
                    output.append(line)
                }
            }
            output.append("\n")
        }

        logger.info { "Manifest:\n$output" }

        resource.bytes = output.toString().toByteArray()
    }
}