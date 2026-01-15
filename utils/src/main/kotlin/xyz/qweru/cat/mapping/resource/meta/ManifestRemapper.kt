package xyz.qweru.cat.mapping.resource.meta

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.Resource
import xyz.qweru.cat.mapping.lookup.MappingLookup
import xyz.qweru.cat.mapping.resource.ResourceRemapper

object ManifestRemapper : ResourceRemapper {
    private val logger = KotlinLogging.logger {}

    override fun remap(resource: Resource, mappings: MappingLookup, config: Configuration) {
        require(resource.name == "META-INF/MANIFEST.MF")
        if (!config.remapManifest) return

        val manifest = String(resource.bytes)
        val output = StringBuilder()
        for (line in manifest.lines()) {
            if (line.startsWith("Main-Class: ")) {
                val mainClass = line
                    .substring(12)
                    .replace(".", "/")
                val remapped = (mappings.get(mainClass) ?: mainClass)
                    .replace("/", ".")
                output.append("Main-Class: ").append(remapped)
            } else {
                output.append(line)
            }
            output.append("\n")
        }

        resource.bytes = output.toString().toByteArray()
    }
}