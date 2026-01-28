package com.sb.arsketch.ar.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import com.sb.arsketch.ar.geometry.LineStripMesh
import com.sb.arsketch.domain.model.Stroke
import java.util.concurrent.ConcurrentHashMap

class StrokeRenderer {

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
    }

    private var program = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0

    private val strokeMeshes = ConcurrentHashMap<String, LineStripMesh>()
    private var currentStrokeMesh: LineStripMesh? = null

    fun initialize(context: Context) {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = ShaderUtil.createProgram(vertexShader, fragmentShader)

        mvpMatrixHandle = GLES30.glGetUniformLocation(program, "u_ModelViewProjection")
        colorHandle = GLES30.glGetUniformLocation(program, "u_Color")
    }

    fun updateStrokes(strokes: List<Stroke>, currentStroke: Stroke?) {
        val currentIds = strokes.map { it.id }.toSet()

        strokeMeshes.keys.filter { it !in currentIds }.forEach { id ->
            strokeMeshes.remove(id)?.release()
        }

        strokes.forEach { stroke ->
            val mesh = strokeMeshes.getOrPut(stroke.id) { LineStripMesh() }
            mesh.updatePoints(stroke.points)
        }

        if (currentStroke != null && currentStroke.points.size >= 2) {
            if (currentStrokeMesh == null) {
                currentStrokeMesh = LineStripMesh()
            }
            currentStrokeMesh?.updatePoints(currentStroke.points)
        } else {
            currentStrokeMesh?.release()
            currentStrokeMesh = null
        }
    }

    fun draw(
        strokes: List<Stroke>,
        currentStroke: Stroke?,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        if (program == 0) return

        GLES30.glUseProgram(program)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        val mvpMatrix = FloatArray(16)

        strokes.forEach { stroke ->
            strokeMeshes[stroke.id]?.let { mesh ->
                mesh.uploadToGPU()
                drawStroke(mesh, stroke, viewMatrix, projectionMatrix, mvpMatrix)
            }
        }

        currentStroke?.let { stroke ->
            currentStrokeMesh?.let { mesh ->
                mesh.uploadToGPU()
                drawStroke(mesh, stroke, viewMatrix, projectionMatrix, mvpMatrix)
            }
        }
    }

    private fun drawStroke(
        mesh: LineStripMesh,
        stroke: Stroke,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        mvpMatrix: FloatArray
    ) {
        if (mesh.getVertexCount() < 2) return

        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        val color = floatArrayOf(
            ((stroke.color shr 16) and 0xFF) / 255f,
            ((stroke.color shr 8) and 0xFF) / 255f,
            (stroke.color and 0xFF) / 255f,
            ((stroke.color shr 24) and 0xFF) / 255f
        )
        GLES30.glUniform4fv(colorHandle, 1, color, 0)

        GLES30.glLineWidth(stroke.thickness * 500f)

        mesh.draw()
    }

    fun clearAll() {
        strokeMeshes.values.forEach { it.release() }
        strokeMeshes.clear()
        currentStrokeMesh?.release()
        currentStrokeMesh = null
    }

    fun release() {
        clearAll()
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
