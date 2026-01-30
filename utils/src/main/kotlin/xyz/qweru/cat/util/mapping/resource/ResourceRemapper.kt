package xyz.qweru.cat.util.mapping.resource

import xyz.qweru.cat.util.config.Configuration
import xyz.qweru.cat.util.jar.Resource
import xyz.qweru.cat.util.mapping.lookup.MappingLookup

interface ResourceRemapper {
    fun remap(resource: Resource, mappings: MappingLookup, config: Configuration)
}