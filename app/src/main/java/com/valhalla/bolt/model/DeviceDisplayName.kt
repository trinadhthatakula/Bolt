package com.valhalla.bolt.model

/** Presentation only: recovery manifests continue to use the original device identity. */
object DeviceDisplayName {
    // Prefer the current product over vendor/boot-image identities on cross-region ROMs.
    // In particular, a POCO F7 boot image may still identify itself as REDMI Turbo 4 Pro.
    private val marketingProperties = listOf(
        "ro.product.marketname",
        "ro.product.odm.marketname",
        "ro.product.vendor.marketname",
        "ro.config.marketing_name",
    )

    suspend fun resolve(executor: CommandExecutor, brand: String, manufacturer: String, model: String): String {
        for (property in marketingProperties) {
            val result = executor.execute("getprop ${shellQuote(property)}", timeoutSeconds = 2)
            val name = result.output.validName()
            if (result.isSuccess && name != null && !name.equals(model, ignoreCase = true)) return fallback(brand, manufacturer, name)
        }
        return fallback(brand, manufacturer, model)
    }

    fun fallback(brand: String, manufacturer: String, model: String): String {
        val make = (brand.validName() ?: manufacturer.validName())?.replaceFirstChar { it.uppercaseChar() }
        val name = model.validName()
        if (make == null) return name ?: "Android device"
        if (name == null) return make
        return if (name.equals(make, ignoreCase = true) || name.startsWith("$make ", ignoreCase = true)) name else "$make $name"
    }

    private fun String.validName(): String? = trim().takeIf {
        it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) && !it.equals("null", ignoreCase = true) && !it.contains('\n')
    }
}
