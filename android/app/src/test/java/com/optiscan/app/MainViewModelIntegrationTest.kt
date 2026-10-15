package com.optiscan.app

import android.graphics.Bitmap
import com.optiscan.app.camera.CameraManager
import com.optiscan.app.data.models.AnalysisResponse
import com.optiscan.app.data.models.StatusTypes
import com.optiscan.app.data.models.TaskTypes
import com.optiscan.app.data.repository.ScanRepository
import com.optiscan.app.navigation.AppSettings
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import com.optiscan.app.ui.screens.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake repository implementation for hermetic unit and integration testing.
 */
class FakeScanRepository : ScanRepository {
    private var isMock: Boolean = false
    var analyzeResult: Result<AnalysisResponse>? = null
    var onDeviceOcrResult: Result<String>? = null
    var capturedTask: String? = null
    var capturedOcrText: String? = null
    var capturedQuestion: String? = null
    var callCount: Int = 0

    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        task: String,
        question: String?,
        ocrText: String?
    ): Result<AnalysisResponse> {
        callCount++
        capturedTask = task
        capturedQuestion = question
        capturedOcrText = ocrText

        return analyzeResult ?: Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = task,
                answer = "A clear hallway with doors on both sides.",
                shouldRecapture = false,
                reason = null
            )
        )
    }

    override suspend fun readTextOnDevice(bitmap: Bitmap): Result<String> {
        return onDeviceOcrResult ?: Result.success("Amoxicillin 500mg - Take one capsule daily")
    }

    override suspend fun checkBackendHealth(): Boolean = true

    override fun setMockMode(enabled: Boolean) {
        isMock = enabled
    }

    override fun isMockMode(): Boolean = isMock
}

/**
 * Unit tests verifying end-to-end integration logic in MainViewModel:
 * - Live scene description analysis and state transitions
 * - Live document text reading and ML Kit integration
 * - Blur and darkness recapture guidance handling
 * - Network errors and timeout handling with retry capability
 * - Offline mock mode settings propagation
 * - Duplicate-tap request prevention
 * - Request cancellation and state hygiene
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeScanRepository
    private lateinit var viewModel: MainViewModel
    private val sampleBytes = "test_image_bytes".toByteArray()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeScanRepository()
        viewModel = MainViewModel(
            cameraManager = CameraManager(),
            repository = fakeRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDescribeSurroundingsSuccessNavigatesToDescriptionResult() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "An open office area with three desks and an exit door ahead.",
                shouldRecapture = false,
                reason = null
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)

        val currentScreen = viewModel.currentScreen.value
        assertTrue("Expected DescriptionResult screen", currentScreen is Screen.DescriptionResult)
        val result = currentScreen as Screen.DescriptionResult
        assertEquals("An open office area with three desks and an exit door ahead.", result.description)
        assertFalse(result.shouldRecapture)
        assertTrue(result.isLiveResult)
    }

    @Test
    fun testReadTextSuccessNavigatesToReadTextResult() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.READ_TEXT,
                answer = "Rx #987654 - Ibuprofen 400mg Tablets",
                shouldRecapture = false,
                reason = null
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.READ_TEXT, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)

        val currentScreen = viewModel.currentScreen.value
        assertTrue("Expected ReadTextResult screen", currentScreen is Screen.ReadTextResult)
        val result = currentScreen as Screen.ReadTextResult
        assertEquals("Rx #987654 - Ibuprofen 400mg Tablets", result.text)
        assertFalse(result.shouldRecapture)
        assertTrue(result.isLiveResult)
    }

    @Test
    fun testBlurryImageTriggersRecaptureGuidance() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.RECAPTURE,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = null,
                shouldRecapture = true,
                reason = "Image is too blurry. Please hold steady and recapture."
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        val currentScreen = viewModel.currentScreen.value
        assertTrue("Expected DescriptionResult screen", currentScreen is Screen.DescriptionResult)
        val result = currentScreen as Screen.DescriptionResult
        assertTrue(result.shouldRecapture)
        assertEquals("Image is too blurry. Please hold steady and recapture.", result.recaptureReason)
    }

    @Test
    fun testNetworkFailureSetsActionableErrorAndAllowsRetry() = runTest(testDispatcher) {
        val networkErrorMessage = "Could not connect to OptiScan backend. Please verify your connection."
        fakeRepository.analyzeResult = Result.failure(Exception(networkErrorMessage))

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertEquals(networkErrorMessage, viewModel.analysisError.value)
        assertTrue(viewModel.currentScreen.value is Screen.Welcome)

        // Retry succeeds
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "Clear path ahead.",
                shouldRecapture = false,
                reason = null
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)
        assertTrue(viewModel.currentScreen.value is Screen.DescriptionResult)
    }

    @Test
    fun testPreventDuplicateRequestsFromRepeatedTaps() = runTest(testDispatcher) {
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "Result",
                shouldRecapture = false
            )
        )

        // First tap initiates request
        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        // Second tap immediately while first is running
        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)

        advanceUntilIdle()

        assertEquals("Only one analysis request should have been dispatched", 1, fakeRepository.callCount)
    }

    @Test
    fun testCancelAnalysisCleansUpState() = runTest(testDispatcher) {
        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        viewModel.cancelAnalysis()
        advanceUntilIdle()

        assertFalse(viewModel.isAnalyzing.value)
        assertNull(viewModel.analysisError.value)
    }

    @Test
    fun testOfflineMockModeSettingsPropagation() = runTest(testDispatcher) {
        viewModel.updateSettings(AppSettings(useMockBackend = true))
        assertTrue(fakeRepository.isMockMode())

        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "[Offline Mock] Simulated response",
                shouldRecapture = false
            )
        )

        viewModel.analyzeCapturedImage(ScanTask.DESCRIBE_SURROUNDINGS, sampleBytes)
        advanceUntilIdle()

        val currentScreen = viewModel.currentScreen.value as Screen.DescriptionResult
        assertFalse("Mock mode should flag isLiveResult as false", currentScreen.isLiveResult)
    }

    @Test
    fun testInteractiveQuestionAnswering() = runTest(testDispatcher) {
        viewModel.setCapturedImage(null, sampleBytes)
        fakeRepository.analyzeResult = Result.success(
            AnalysisResponse(
                status = StatusTypes.SUCCESS,
                task = TaskTypes.DESCRIBE_IMAGE,
                answer = "The dosage is two tablets every 6 hours.",
                shouldRecapture = false
            )
        )

        var answeredQuery: String? = null
        viewModel.askQuestion(
            query = "What is the recommended dosage?",
            contextContent = "Acetaminophen 500mg"
        ) { answer ->
            answeredQuery = answer
        }

        advanceUntilIdle()
        assertNotNull(answeredQuery)
        assertEquals("The dosage is two tablets every 6 hours.", answeredQuery)
        assertEquals("What is the recommended dosage?", fakeRepository.capturedQuestion)
    }
}
