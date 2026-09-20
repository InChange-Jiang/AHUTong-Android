package com.ahu.ahutong.di

import com.ahu.ahutong.data.session.AhuSession
import com.ahu.ahutong.data.session.CredentialVault
import com.ahu.ahutong.data.session.DefaultAhuSession
import com.ahu.ahutong.data.session.SecureCredentialVault
import com.ahu.ahutong.data.session.SessionIdentity
import com.ahu.ahutong.data.session.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** :core:auth 的组合根；三个生产适配器都是 object，因此统一用 @Provides 接线。 */
@Module
@InstallIn(SingletonComponent::class)
object AuthWiringModule {

    @Provides
    @Singleton
    fun provideSessionIdentity(): SessionIdentity = SessionStore

    @Provides
    @Singleton
    fun provideAhuSession(): AhuSession = DefaultAhuSession

    @Provides
    @Singleton
    fun provideCredentialVault(): CredentialVault = SecureCredentialVault
}
