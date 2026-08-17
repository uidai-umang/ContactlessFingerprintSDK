package `in`.gov.uidai.embedding.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.gov.uidai.embedding.DemoFingerEmbedderImpl
import `in`.gov.uidai.embedding.FingerEmbedder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EmbedderModule {
    
    @Binds
    @Singleton
    abstract fun bindsFingerEmbedder(
        fingerEmbedderImpl: DemoFingerEmbedderImpl
    ): FingerEmbedder
}
