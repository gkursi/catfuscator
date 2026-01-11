package xyz.qweru.cat.mapping.resource

import xyz.qweru.cat.config.Configuration
import xyz.qweru.cat.jar.Resource
import xyz.qweru.cat.mapping.lookup.MappingLookup

interface ResourceRemapper {
    fun remap(resource: Resource, mappings: MappingLookup, config: Configuration)
}