package com.optiscan.app

import com.google.gson.Gson
import com.optiscan.app.data.models.AnalysisResponse
import com.optiscan.app.data.models.HealthResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that Android client data models exactly match the backend JSON contract.
 */
class ModelContractTest {

    private val gson = Gson()

    @Test
    fun testHealthResponseContract() {
        val json = """
            {
              "status": "ok",
              "service": "optiscan-backend"
            }
        """.trimIndent()

        val response = gson.fromJson(json, HealthResponse::class.java)
        assertEquals("ok", response.status)
        assertEquals("optiscan-backend", response.service)
    }

    @Test
    fun testAnalyzeSuccessContract() {
        val json = """
            {
              "status": "success",
              "task": "describe_image",
              "answer": "A person is standing near a doorway.",
              "should_recapture": false,
              "reason": null
            }
        """.trimIndent()

        val response = gson.fromJson(json, AnalysisResponse::class.java)
        assertEquals("success", response.status)
        assertEquals("describe_image", response.task)
        assertEquals("A person is standing near a doorway.", response.answer)
        assertFalse(response.shouldRecapture)
        assertNull(response.reason)
    }

    @Test
    fun testAnalyzeRecaptureContract() {
        val json = """
            {
              "status": "recapture",
              "task": "describe_image",
              "answer": null,
              "should_recapture": true,
              "reason": "Image is too blurry. Please hold steady and recapture."
            }
        """.trimIndent()

        val response = gson.fromJson(json, AnalysisResponse::class.java)
        assertEquals("recapture", response.status)
        assertEquals("describe_image", response.task)
        assertNull(response.answer)
        assertTrue(response.shouldRecapture)
        assertEquals("Image is too blurry. Please hold steady and recapture.", response.reason)
    }

    @Test
    fun testAnalyzeHindiResponseWithMedicationSafetyContract() {
        val json = """
            {
              "status": "success",
              "task": "read_text",
              "answer": "दवा का नाम Paracetamol 500mg है। खुराक: 2 tablets हर 6 घंटे में।",
              "should_recapture": false,
              "reason": null
            }
        """.trimIndent()

        val response = gson.fromJson(json, AnalysisResponse::class.java)
        assertEquals("success", response.status)
        assertEquals("read_text", response.task)
        assertEquals("दवा का नाम Paracetamol 500mg है। खुराक: 2 tablets हर 6 घंटे में।", response.answer)
        assertFalse(response.shouldRecapture)
        assertNull(response.reason)
    }
}
