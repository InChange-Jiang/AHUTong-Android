package com.ahu.ahutong.personalization.di

import com.ahu.ahutong.personalization.evaluation.PairedShadowModelEvaluator
import com.ahu.ahutong.personalization.evaluation.ShadowModelEvaluator
import com.ahu.ahutong.personalization.training.KotlinOnDeviceTrainer
import com.ahu.ahutong.personalization.training.OnDeviceTrainer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PredictionBindingModule {
    @Binds
    @Singleton
    abstract fun bindTrainer(implementation: KotlinOnDeviceTrainer): OnDeviceTrainer

    @Binds
    @Singleton
    abstract fun bindEvaluator(implementation: PairedShadowModelEvaluator): ShadowModelEvaluator
}
