package app.gov.uidai.capture.di

import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.config.BrightnessSettings
import app.gov.uidai.capture.domain.config.FingerSettings
import app.gov.uidai.capture.domain.config.GlareSettings
import app.gov.uidai.capture.domain.config.SegmentationSettings
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.config.CameraSettings
import app.gov.uidai.capture.ui.camera.config.SessionSettings
import app.gov.uidai.capture.ui.settings.provider.SettingProvider
import app.gov.uidai.capture.usecase.ProcessingSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @IntoSet
    fun provideCamera(preferenceStore: PreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, CameraSettings)

    @Provides
    @IntoSet
    fun provideSessionTimer(preferenceStore: PreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, SessionSettings)

    @Provides
    @IntoSet
    fun provideGlobal(preferenceStore: PreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, ProcessingSettings)

    @Provides
    @IntoSet
    fun provideFinger(preferenceStore: PreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, FingerSettings)

    @Provides
    @IntoSet
    fun provideGlare(preferenceStore: PreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, GlareSettings)

    @Provides
    @IntoSet
    fun provideBrightness(preferenceStore: PreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, BrightnessSettings)

    /*@Provides @IntoSet
    fun provideLiveness(preferenceStore: DebugpreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, LivenessCheck.Settings)*/

    @Provides
    @IntoSet
    fun provideBlur(preferenceStore: PreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, BlurSettings)

    @Provides
    @IntoSet
    fun provideSegmentation(preferenceStore: PreferenceStore): SettingProvider =
        SettingProvider(preferenceStore, SegmentationSettings)
}