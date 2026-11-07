package ua.ecowi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EcoFlowSignerTest {

    private val nested: Map<String, Any?> = mapOf(
        "sn" to "D3C1ZE1AB2CD0001",
        "params" to mapOf(
            "quotas" to listOf("pd.soc", "inv.outputWatts"),
            "id" to 66,
            "cmdSet" to 32,
        ),
    )

    @Test
    fun flattensAndSortsNestedParams() {
        assertEquals(
            "params.cmdSet=32&params.id=66&params.quotas[0]=pd.soc&params.quotas[1]=inv.outputWatts&sn=D3C1ZE1AB2CD0001",
            EcoFlowSigner.canonicalQuery(nested),
        )
    }

    @Test
    fun signsNestedParams() {
        // Reference value computed with Python's hmac/hashlib on the same string.
        assertEquals(
            "28edd7345ce5782790ff05943165fe8418a386f8e3d3c30352765ae1e78ec0f5",
            EcoFlowSigner.sign(nested, "AK123", "SK-secret", "345164", "1730900000000"),
        )
    }

    @Test
    fun signsWithoutParams() {
        assertEquals(
            "f8a01ae1b11905ba144a6e3564b225f7a956f6f965c44a9222106fe13f8b7dc4",
            EcoFlowSigner.sign(emptyMap(), "AK123", "SK-secret", "345164", "1730900000000"),
        )
    }
}
