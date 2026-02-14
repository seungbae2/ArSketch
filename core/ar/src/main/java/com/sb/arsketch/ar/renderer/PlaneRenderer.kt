package com.sb.arsketch.ar.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 감지된 평면을 시각화하는 렌더러
 */
class PlaneRenderer {

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 a_Position;
            uniform mat4 u_ModelViewProjection;
            void main() {
                gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            uniform vec4 u_Color;
            out vec4 fragColor;
            void main() {
                fragColor = u_Color;
            }
        """

        // 평면 색상 (반투명 파란색)
        private val PLANE_COLOR = floatArrayOf(0.0f, 0.6f, 1.0f, 0.3f)

        // 평면 테두리 색상 (더 진한 파란색)
        private val BORDER_COLOR = floatArrayOf(0.0f, 0.4f, 0.8f, 0.8f)
    }

    private var program = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0

    private var vertexBuffer: FloatBuffer? = null
    private var borderBuffer: FloatBuffer? = null

    private val modelMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun initialize(context: Context) {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = ShaderUtil.createProgram(vertexShader, fragmentShader)

        mvpMatrixHandle = GLES30.glGetUniformLocation(program, "u_ModelViewProjection")
        colorHandle = GLES30.glGetUniformLocation(program, "u_Color")

        Timber.d("PlaneRenderer 초기화 완료")
    }

    fun draw(
        planes: Collection<Plane>,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        if (program == 0) return

        GLES30.glUseProgram(program)

        // 블렌딩 활성화 (투명도 지원)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // 깊이 쓰기 비활성화 (투명 평면이 다른 객체를 가리지 않도록)
        GLES30.glDepthMask(false)

        for (plane in planes) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            if (plane.subsumedBy != null) continue  // 다른 평면에 병합된 경우 스킵

            drawPlane(plane, viewMatrix, projectionMatrix)
        }

        // 원래 상태로 복원
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawPlane(
        plane: Plane,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        val polygon = plane.polygon ?: return
        if (polygon.remaining() < 6) return  // 최소 3개 점 필요

        val pose = plane.centerPose
        pose.toMatrix(modelMatrix, 0)

        // MVP 계산
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 폴리곤 정점 추출
        val numPoints = polygon.remaining() / 2
        val vertices = FloatArray(numPoints * 3)

        polygon.rewind()
        for (i in 0 until numPoints) {
            vertices[i * 3] = polygon.get()      // x
            vertices[i * 3 + 1] = 0f             // y (평면 위)
            vertices[i * 3 + 2] = polygon.get()  // z
        }

        // 버텍스 버퍼 생성
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        buffer.position(0)

        // 버텍스 속성 설정
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, buffer)

        // 평면 채우기 (삼각형 팬)
        GLES30.glUniform4fv(colorHandle, 1, PLANE_COLOR, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, numPoints)

        // 테두리 그리기 (라인 루프)
        GLES30.glUniform4fv(colorHandle, 1, BORDER_COLOR, 0)
        GLES30.glLineWidth(3f)
        GLES30.glDrawArrays(GLES30.GL_LINE_LOOP, 0, numPoints)

        GLES30.glDisableVertexAttribArray(0)
    }

    fun release() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)
        return shader
    }
}
