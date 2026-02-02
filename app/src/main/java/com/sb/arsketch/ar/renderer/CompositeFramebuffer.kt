package com.sb.arsketch.ar.renderer

import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES30
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FBO(Framebuffer Object)를 사용한 오프스크린 렌더링 클래스.
 * ARCore 카메라 배경과 AR 드로잉을 합성하여 Bitmap으로 추출합니다.
 *
 * @param width FBO 너비 (픽셀)
 * @param height FBO 높이 (픽셀)
 */
class CompositeFramebuffer(
    val width: Int,
    val height: Int
) {
    private var fboId: Int = 0
    private var colorTextureId: Int = 0
    private var depthRboId: Int = 0

    private var isInitialized = false

    // Bitmap 변환을 위한 재사용 버퍼
    private var pixelBuffer: ByteBuffer? = null
    private val flipMatrix = Matrix().apply {
        postScale(1f, -1f)
    }

    /**
     * FBO 초기화. GL 컨텍스트에서 호출해야 합니다.
     */
    fun initialize() {
        if (isInitialized) {
            Timber.w("CompositeFramebuffer already initialized")
            return
        }

        // 1. FBO 생성
        val fboArray = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArray, 0)
        fboId = fboArray[0]
        ShaderUtil.checkGLError("glGenFramebuffers")

        // 2. Color Texture 생성
        val texArray = IntArray(1)
        GLES30.glGenTextures(1, texArray, 0)
        colorTextureId = texArray[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        ShaderUtil.checkGLError("Create color texture")

        // 3. Depth Renderbuffer 생성
        val rboArray = IntArray(1)
        GLES30.glGenRenderbuffers(1, rboArray, 0)
        depthRboId = rboArray[0]

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRboId)
        GLES30.glRenderbufferStorage(
            GLES30.GL_RENDERBUFFER,
            GLES30.GL_DEPTH_COMPONENT24,
            width,
            height
        )
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)
        ShaderUtil.checkGLError("Create depth renderbuffer")

        // 4. FBO에 텍스처와 렌더버퍼 연결
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            colorTextureId,
            0
        )
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_RENDERBUFFER,
            depthRboId
        )

        // 5. FBO 상태 검증
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not complete: $status")
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        ShaderUtil.checkGLError("Initialize FBO")

        // 6. 픽셀 버퍼 할당
        pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        isInitialized = true
        Timber.d("CompositeFramebuffer initialized: ${width}x${height}")
    }

    /**
     * FBO를 바인딩하여 렌더링 대상으로 설정합니다.
     * 이 메서드 호출 후 렌더링하면 FBO에 그려집니다.
     */
    fun bind() {
        if (!isInitialized) {
            Timber.w("CompositeFramebuffer not initialized")
            return
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glViewport(0, 0, width, height)
    }

    /**
     * FBO 바인딩 해제. 화면 렌더링으로 복귀합니다.
     *
     * @param screenWidth 화면 너비 (뷰포트 복원용)
     * @param screenHeight 화면 높이 (뷰포트 복원용)
     */
    fun unbind(screenWidth: Int, screenHeight: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenWidth, screenHeight)
    }

    /**
     * FBO 내용을 Bitmap으로 읽어옵니다.
     * 동기 방식으로 glReadPixels를 사용합니다.
     *
     * @return FBO 내용이 담긴 Bitmap (ARGB_8888)
     */
    fun readToBitmap(): Bitmap? {
        if (!isInitialized) {
            Timber.w("CompositeFramebuffer not initialized")
            return null
        }

        val buffer = pixelBuffer ?: return null

        // FBO에서 픽셀 읽기
        bind()
        buffer.rewind()
        GLES30.glReadPixels(
            0, 0, width, height,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )
        buffer.rewind()

        // ByteBuffer → Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // OpenGL은 좌하단 원점이므로 Y축 뒤집기
        val flippedBitmap = Bitmap.createBitmap(
            bitmap,
            0, 0,
            width, height,
            flipMatrix,
            true
        )

        // 원본 bitmap 재활용 (flippedBitmap과 다른 경우만)
        if (flippedBitmap != bitmap) {
            bitmap.recycle()
        }

        return flippedBitmap
    }

    /**
     * FBO 리소스 해제.
     * GL 컨텍스트에서 호출해야 합니다.
     */
    fun destroy() {
        if (!isInitialized) return

        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            fboId = 0
        }
        if (colorTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(colorTextureId), 0)
            colorTextureId = 0
        }
        if (depthRboId != 0) {
            GLES30.glDeleteRenderbuffers(1, intArrayOf(depthRboId), 0)
            depthRboId = 0
        }

        pixelBuffer = null
        isInitialized = false
        Timber.d("CompositeFramebuffer destroyed")
    }

    /**
     * FBO 초기화 상태 확인
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Color texture ID 반환 (다른 셰이더에서 샘플링용)
     */
    fun getColorTextureId(): Int = colorTextureId
}
