package com.chomu.aiagent.di

import com.chomu.aiagent.data.repository.LLMRepository
import com.chomu.aiagent.data.repository.LLMRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindLLMRepository(impl: LLMRepositoryImpl): LLMRepository
}
