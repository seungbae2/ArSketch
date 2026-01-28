package com.sb.arsketch.ar.renderer

import android.content.Context
import android.opengl.GLES30
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

object ShaderUtil {

    fun loadGLShader(
        context: Context,
        type: Int,
        filename: String
    ): Int {
        val code = readShaderFile(context, filename)
        return compileShader(type, code)
    }

    fun createProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES30.glCreateProgram()
        checkGLError("glCreateProgram")

        GLES30.glAttachShader(program, vertexShader)
        checkGLError("glAttachShader vertex")

        GLES30.glAttachShader(program, fragmentShader)
        checkGLError("glAttachShader fragment")

        GLES30.glLinkProgram(program)
        checkGLError("glLinkProgram")

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES30.GL_TRUE) {
            val error = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("프로그램 링크 실패: $error")
        }

        return program
    }

    private fun readShaderFile(context: Context, filename: String): String {
        return context.assets.open("shaders/$filename").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        checkGLError("glCreateShader")

        GLES30.glShaderSource(shader, code)
        checkGLError("glShaderSource")

        GLES30.glCompileShader(shader)
        checkGLError("glCompileShader")

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES30.GL_TRUE) {
            val error = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("셰이더 컴파일 실패: $error")
        }

        return shader
    }

    fun checkGLError(operation: String) {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Timber.e("OpenGL 오류 ($operation): $error")
            throw RuntimeException("OpenGL 오류: $operation, code=$error")
        }
    }
}
