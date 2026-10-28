package com.catsblock.weather.ui.atmosphere;

import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public final class RuntimeShaderUniforms {
    private RuntimeShaderUniforms() {}

    public static void setFloatUniform(RuntimeShader shader, String name, float[] values) {
        shader.setFloatUniform(name, values);
    }
}
