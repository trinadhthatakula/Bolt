package com.valhalla.bolt.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeviceDisplayNameTest {
    @Test fun currentProductWinsOverCrossRegionRomIdentities(): Unit = runBlocking {
        val properties = mapOf("ro.product.marketname" to "POCO F7", "ro.product.odm.marketname" to "REDMI Turbo 4 Pro")
        val executor = object : CommandExecutor {
            override suspend fun execute(script: String, timeoutSeconds: Long, onOutput: (String) -> Unit) =
                CommandResult(0, properties[script.removePrefix("getprop '").removeSuffix("'")].orEmpty())
        }
        assertEquals("POCO F7", DeviceDisplayName.resolve(executor, "POCO", "Xiaomi", "25053PC47G"))
    }

    @Test fun missingMarketingPropertiesFallBackWithoutDuplicatingTheMake(): Unit = runBlocking {
        val executor = object : CommandExecutor {
            override suspend fun execute(script: String, timeoutSeconds: Long, onOutput: (String) -> Unit) = CommandResult(1, "unknown")
        }
        assertEquals("Google Pixel 9", DeviceDisplayName.resolve(executor, "google", "Google", "Pixel 9"))
        assertEquals("POCO F7", DeviceDisplayName.resolve(executor, "POCO", "Xiaomi", "POCO F7"))
    }

    @Test fun unknownBuildFieldsUseTheManufacturerOrGenericName() {
        assertEquals("Samsung SM-S938B", DeviceDisplayName.fallback("unknown", "samsung", "SM-S938B"))
        assertEquals("Android device", DeviceDisplayName.fallback("unknown", "unknown", "unknown"))
    }
}
