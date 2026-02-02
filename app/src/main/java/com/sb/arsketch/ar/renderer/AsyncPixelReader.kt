package com.sb.arsketch.ar.renderer

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES30
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PBO(Pixel Buffer Object)를 사용한 비동기 픽셀 읽기.
 *
 * glReadPixels의 GPU 스톨을 방지하기 위해 더블 버퍼링 PBO를 사용합니다.
 * - Frame N: PBO A에 비동기 읽기 시작
 * - Frame N+1: PBO B에 비동기 읽기 시작, PBO A 매핑하여 데이터 획득
 *
 * 이 방식은 1프레임 지연이 발생하지만 GPU 스톨을 완전히 제거합니다.
 *
 * @param width 읽을 영역의 너비
 * @param height 읽을 영역의 높이
 */
class AsyncPixelReader(
    private var width: Int,
    private var height: Int
) {
    private val pboIds = IntArray(2)  // 더블 버퍼링
    private var readIndex = 0   // 현재 읽기 중인 PBO
    private var mapIndex = 1    // 매핑할 PBO (이전 프레임)

    private var isInitialized = false
    private var frameCount = 0

    // 비트맵 Y축 뒤집기용 Matrix
    private val flipMatrix = Matrix().apply {
        postScale(1f, -1f)
    }

    /**
     * PBO 초기화.
     * GL 컨텍스트에서 호출해야 합니다.
     */
    fun initialize() {
        if (isInitialized) {
            destroy()
        }

        val bufferSize = width * height * 4  // RGBA

        GLES30.glGenBuffers(2, pboIds, 0)

        for (i in 0..1) {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[i])
            GLES30.glBufferData(
                GLES30.GL_PIXEL_PACK_BUFFER,
                bufferSize,
                null,
                GLES30.GL_STREAM_READ
            )
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)

        isInitialized = true
        frameCount = 0

        Timber.d("AsyncPixelReader initialized: ${width}x${height}, PBOs: ${pboIds.contentToString()}")
    }

    /**
     * 해상도 변경.
     * GL 컨텍스트에서 호출해야 합니다.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth == width && newHeight == height) return

        width = newWidth
        height = newHeight

        if (isInitialized) {
            initialize()  // 재초기화
        }

        Timber.d("AsyncPixelReader resized: ${width}x${height}")
    }

    /**
     * 비동기 픽셀 읽기 시작 (논블로킹).
     * 현재 바인딩된 FBO에서 픽셀을 PBO로 읽습니다.
     *
     * GL 컨텍스트에서 호출해야 합니다.
     */
    fun readPixelsAsync() {
        if (!isInitialized) {
            Timber.w("AsyncPixelReader not initialized")
            return
        }

        // 현재 PBO에 비동기 읽기 시작
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[readIndex])
        GLES30.glReadPixels(
            0, 0, width, height,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            0  // PBO 오프셋 (0부터 시작)
        )
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
    }

    /**
     * 이전 프레임 결과를 Bitmap으로 가져옵니다.
     * 첫 프레임에서는 null을 반환합니다 (PBO가 아직 준비되지 않음).
     *
     * GL 컨텍스트에서 호출해야 합니다.
     *
     * @return 비트맵 (Y축 뒤집힌 상태로 보정됨), 또는 아직 준비되지 않은 경우 null
     */
    fun getLastFrameBitmap(): Bitmap? {
        if (!isInitialized) {
            Timber.w("AsyncPixelReader not initialized")
            return null
        }

        // 첫 프레임은 스킵 (이전 데이터 없음)
        if (frameCount == 0) {
            frameCount++
            swapBuffers()
            return null
        }

        frameCount++

        // 이전 PBO 매핑
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[mapIndex])

        val mappedBuffer = GLES30.glMapBufferRange(
            GLES30.GL_PIXEL_PACK_BUFFER,
            0,
            width * height * 4,
            GLES30.GL_MAP_READ_BIT
        ) as? ByteBuffer

        if (mappedBuffer == null) {
            Timber.e("Failed to map PBO buffer")
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
            swapBuffers()
            return null
        }

        // ByteBuffer → Bitmap
        mappedBuffer.order(ByteOrder.nativeOrder())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(mappedBuffer)

        // 매핑 해제
        GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)

        // 버퍼 스왑
        swapBuffers()

        // Y축 뒤집기 (OpenGL은 bottom-left origin)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, flipMatrix, true).also {
            if (it !== bitmap) {
                bitmap.recycle()
            }
        }
    }

    /**
     * 동기 방식으로 현재 프레임을 즉시 읽습니다 (폴백용).
     * PBO를 사용하지 않으며 GPU 스톨이 발생합니다.
     */
    fun readPixelsSync(): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        GLES30.glReadPixels(
            0, 0, width, height,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )

        buffer.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // Y축 뒤집기
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, flipMatrix, true).also {
            if (it !== bitmap) {
                bitmap.recycle()
            }
        }
    }

    private fun swapBuffers() {
        readIndex = mapIndex.also { mapIndex = readIndex }
    }

    /**
     * 리소스 해제.
     * GL 컨텍스트에서 호출해야 합니다.
     */
    fun destroy() {
        if (isInitialized) {
            GLES30.glDeleteBuffers(2, pboIds, 0)
            pboIds[0] = 0
            pboIds[1] = 0
            isInitialized = false
            frameCount = 0
            Timber.d("AsyncPixelReader destroyed")
        }
    }
}
