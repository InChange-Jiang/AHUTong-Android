package com.ahu.ahutong.di

import com.ahu.ahutong.data.repository.ManagerRepositoryIndex
import com.ahu.ahutong.ui.state.IoDispatcher
import com.ahu.ahutong.data.repository.RepositoryIndex
import com.ahu.ahutong.ui.state.AndroidRepositoryFileAccess
import com.ahu.ahutong.ui.state.RepositoryFileAccess
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 仓库索引 feature 的接线：把接口绑到 :app 里的实现上。
 *
 * 放在 `di/` 而不是 `data/`：这些实现一个来自数据层、一个来自界面侧，
 * 只有组合根同时认识两边；此前把它们塞进 `data/` 会违反 R1（data 不得依赖 ui）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryWiringModule {

    @Binds
    @Singleton
    abstract fun bindRepositoryIndex(implementation: ManagerRepositoryIndex): RepositoryIndex

    /** feature 只认识"读文件/打开文件"这两个动作，框架细节留在实现侧。 */
    @Binds
    @Singleton
    abstract fun bindRepositoryFileAccess(
        implementation: AndroidRepositoryFileAccess
    ): RepositoryFileAccess

    companion object {

        /** feature 的 ViewModel 只要求"一个 IO 调度器"，具体是谁由组合根决定。 */
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
