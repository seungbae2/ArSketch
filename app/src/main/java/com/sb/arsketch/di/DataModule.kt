package com.sb.arsketch.di

import android.content.Context
import androidx.room.Room
import com.sb.arsketch.data.local.db.ArSketchDatabase
import com.sb.arsketch.data.local.db.SessionDao
import com.sb.arsketch.data.local.db.StrokeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ArSketchDatabase {
        return Room.databaseBuilder(
            context,
            ArSketchDatabase::class.java,
            ArSketchDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSessionDao(database: ArSketchDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideStrokeDao(database: ArSketchDatabase): StrokeDao {
        return database.strokeDao()
    }
}
