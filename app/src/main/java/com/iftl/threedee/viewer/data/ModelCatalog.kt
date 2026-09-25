package com.iftl.threedee.viewer.data

/** The five models bundled under assets/models, as shipped by Infusory for this task. */
data class CatalogEntry(val displayName: String, val assetPath: String)

object ModelCatalog {
    val entries: List<CatalogEntry> =
        listOf(
            CatalogEntry("Bulb", "models/Bulb.glb"),
            CatalogEntry("Fiagena", "models/Fiagena.glb"),
            CatalogEntry("Lungs", "models/Lungs.glb"),
            CatalogEntry("Microscope", "models/Microscope.glb"),
            CatalogEntry("Solar System", "models/solarsystem.glb"),
        )
}
