package com.example.tflite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArchitectureTest {

    @Test
    fun ssdMobileNetV1_isTheOnlyBundledModel() {
        val bundled = ModelArchitecture.entries.filter(ModelArchitecture::isBundled)

        assertEquals(listOf(ModelArchitecture.MOBILENET_SSD_V1), bundled)
        assertEquals(
            "mobilenet_ssd.tflite",
            ModelArchitecture.MOBILENET_SSD_V1.assetFileName
        )
        assertTrue(ModelArchitecture.MOBILENET_SSD_V1.isSelectable)
    }

    @Test
    fun futureModelSlots_areVisibleButNotSelectable() {
        val futureModels = ModelArchitecture.entries
            .filterNot { it == ModelArchitecture.MOBILENET_SSD_V1 }

        assertEquals(listOf(ModelArchitecture.CUSTOM_YOLO), futureModels)
        assertTrue(futureModels.all { it.assetFileName == null })
        assertTrue(futureModels.all { it.availabilityLabel == "导入模型" })
        assertFalse(futureModels.any(ModelArchitecture::isSelectable))
    }
}
