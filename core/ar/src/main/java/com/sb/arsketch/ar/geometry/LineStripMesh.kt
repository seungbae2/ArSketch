package com.sb.arsketch.ar.geometry

import android.opengl.GLES30
import com.sb.arsketch.domain.model.Point3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class LineStripMesh {

    companion object {
        private const val COORDS_PER_VERTEX = 3
        private const val BYTES_PER_FLOAT = 4
    }

    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount = 0
    private var vboId = 0
    private var isDirty = true

    fun updatePoints(points: List<Point3D>) {
        if (points.size < 2) {
            vertexCount = 0
            return
        }

        val vertexData = FloatArray(points.size * COORDS_PER_VERTEX)
        points.forEachIndexed { index, point ->
            val offset = index * COORDS_PER_VERTEX
            vertexData[offset] = point.x
            vertexData[offset + 1] = point.y
            vertexData[offset + 2] = point.z
        }

        vertexBuffer = ByteBuffer
            .allocateDirect(vertexData.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData)
                position(0)
            }

        vertexCount = points.size
        isDirty = true
    }

    fun uploadToGPU() {
        if (!isDirty || vertexBuffer == null) return

        if (vboId == 0) {
            val vbos = IntArray(1)
            GLES30.glGenBuffers(1, vbos, 0)
            vboId = vbos[0]
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexCount * COORDS_PER_VERTEX * BYTES_PER_FLOAT,
            vertexBuffer,
            GLES30.GL_DYNAMIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        isDirty = false
    }

    fun draw() {
        if (vertexCount < 2 || vboId == 0) return

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            COORDS_PER_VERTEX,
            GLES30.GL_FLOAT,
            false,
            0,
            0
        )

        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, vertexCount)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun getVertexCount(): Int = vertexCount

    fun release() {
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        vertexBuffer = null
        vertexCount = 0
    }
}
