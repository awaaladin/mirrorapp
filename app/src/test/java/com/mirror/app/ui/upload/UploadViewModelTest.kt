package com.mirror.app.ui.upload

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import app.cash.turbine.test
import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.landmark.FaceLandmarkerHelper
import com.mirror.app.domain.model.FaceLandmarkResult
import com.mirror.app.domain.model.NormalizedLandmark
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fakeUri: Uri = mockk()
    private val fakeContentResolver: ContentResolver = mockk()
    private val fakeBitmap: Bitmap = mockk(relaxed = true)
    private val faceLandmarkerHelper: FaceLandmarkerHelper = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(decodeBitmap: suspend (Uri, ContentResolver) -> Bitmap? = { _, _ -> fakeBitmap }) =
        UploadViewModel(faceLandmarkerHelper, decodeBitmap = decodeBitmap)

    @Test
    fun `initial state is Idle`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value is UploadUiState.Idle)
    }

    @Test
    fun `successful detection emits Detecting then Detected with landmarks`() = runTest {
        val landmarkResult = FaceLandmarkResult(
            points = listOf(NormalizedLandmark(0.5f, 0.5f)),
            imageWidth = 100,
            imageHeight = 100
        )
        coEvery { faceLandmarkerHelper.detect(fakeBitmap) } returns AppResult.Success(landmarkResult)

        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem() is UploadUiState.Idle)
            vm.onPhotoPicked(fakeUri, fakeContentResolver)
            assertTrue(awaitItem() is UploadUiState.Detecting)
            val detected = awaitItem() as UploadUiState.Detected
            assertEquals(landmarkResult, detected.landmarks)
            assertTrue(detected.showDebugOverlay)
        }
    }

    @Test
    fun `no face detected maps to NO_FACE_DETECTED error state`() = runTest {
        coEvery { faceLandmarkerHelper.detect(fakeBitmap) } returns AppResult.Failure(AppError.NoFaceDetected)

        val vm = viewModel()
        vm.uiState.test {
            awaitItem() // Idle
            vm.onPhotoPicked(fakeUri, fakeContentResolver)
            awaitItem() // Detecting
            val error = awaitItem() as UploadUiState.Error
            assertEquals(UploadErrorMessage.NO_FACE_DETECTED, error.message)
            assertEquals(fakeBitmap, error.photo)
        }
    }

    @Test
    fun `image decode failure emits IMAGE_LOAD_FAILED with no photo`() = runTest {
        val vm = viewModel(decodeBitmap = { _, _ -> null })
        vm.uiState.test {
            awaitItem() // Idle
            vm.onPhotoPicked(fakeUri, fakeContentResolver)
            awaitItem() // Detecting
            val error = awaitItem() as UploadUiState.Error
            assertEquals(UploadErrorMessage.IMAGE_LOAD_FAILED, error.message)
            assertNull(error.photo)
        }
    }

    @Test
    fun `toggleDebugOverlay flips the flag only in Detected state`() = runTest {
        val landmarkResult = FaceLandmarkResult(points = emptyList(), imageWidth = 10, imageHeight = 10)
        coEvery { faceLandmarkerHelper.detect(fakeBitmap) } returns AppResult.Success(landmarkResult)

        val vm = viewModel()
        vm.toggleDebugOverlay() // no-op while Idle
        assertTrue(vm.uiState.value is UploadUiState.Idle)

        vm.onPhotoPicked(fakeUri, fakeContentResolver)
        val detectedBefore = vm.uiState.value as UploadUiState.Detected
        assertTrue(detectedBefore.showDebugOverlay)

        vm.toggleDebugOverlay()
        val detectedAfter = vm.uiState.value as UploadUiState.Detected
        assertEquals(false, detectedAfter.showDebugOverlay)
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        val landmarkResult = FaceLandmarkResult(points = emptyList(), imageWidth = 10, imageHeight = 10)
        coEvery { faceLandmarkerHelper.detect(fakeBitmap) } returns AppResult.Success(landmarkResult)

        val vm = viewModel()
        vm.onPhotoPicked(fakeUri, fakeContentResolver)
        assertTrue(vm.uiState.value is UploadUiState.Detected)

        vm.reset()
        assertTrue(vm.uiState.value is UploadUiState.Idle)
    }
}
