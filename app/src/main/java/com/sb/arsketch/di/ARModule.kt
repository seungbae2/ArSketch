package com.sb.arsketch.di

import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.ar.util.AirDrawingProjector
import com.sb.arsketch.ar.util.HitTestHelper
import com.sb.arsketch.ar.util.TouchToWorldConverter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ARModule {

    @Provides
    @Singleton
    fun provideARSessionManager(): ARSessionManager {
        return ARSessionManager()
    }

    @Provides
    @Singleton
    fun provideHitTestHelper(): HitTestHelper {
        return HitTestHelper()
    }

    @Provides
    @Singleton
    fun provideAirDrawingProjector(): AirDrawingProjector {
        return AirDrawingProjector()
    }

    @Provides
    @Singleton
    fun provideTouchToWorldConverter(
        hitTestHelper: HitTestHelper,
        airDrawingProjector: AirDrawingProjector
    ): TouchToWorldConverter {
        return TouchToWorldConverter(hitTestHelper, airDrawingProjector)
    }

    @Provides
    @Singleton
    fun provideDrawingController(
        touchToWorldConverter: TouchToWorldConverter
    ): DrawingController {
        return DrawingController(touchToWorldConverter)
    }
}
