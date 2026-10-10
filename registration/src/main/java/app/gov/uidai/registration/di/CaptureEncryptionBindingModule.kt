package app.gov.uidai.registration.di

import app.gov.uidai.registration.usecase.CaptureEncryption
import app.gov.uidai.registration.usecase.impl.KeystoreCaptureEncryption
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureEncryptionBindingModule {
    @Binds
    abstract fun bindCaptureEncryption(
        impl: KeystoreCaptureEncryption
    ): CaptureEncryption
}