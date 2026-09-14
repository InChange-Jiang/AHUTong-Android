package com.ahu.ahutong.personalization.di

import com.ahu.ahutong.personalization.evaluation.PairedShadowModelEvaluator
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import com.ahu.ahutong.personalization.preset.AppPresetSuggestions
import com.ahu.ahutong.personalization.preset.PresetSuggestions
import com.ahu.ahutong.personalization.runtime.AppBehaviorRecorder
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

    /** 界面只认识这个窄接口，端侧运行时留在实现侧。 */
    @Binds
    @Singleton
    abstract fun bindBehaviorRecorder(implementation: AppBehaviorRecorder): BehaviorRecorder

    /** 本地预设建议：界面只认识"给我几个建议"这一层。 */
    @Binds
    @Singleton
    abstract fun bindPresetSuggestions(
        implementation: AppPresetSuggestions
    ): PresetSuggestions
}
