package app.gov.uidai.capture.di

import app.gov.uidai.capture.domain.config.BrightnessConfig
import app.gov.uidai.capture.domain.config.BrightnessSettings
import app.gov.uidai.capture.domain.config.GlareConfig
import app.gov.uidai.capture.domain.config.GlareSettings
import app.gov.uidai.capture.pref.PreferenceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {

    @Provides
    fun getBrightnessConfig(
        preferenceStore: PreferenceStore
    ): BrightnessConfig {
        return BrightnessConfig(
            enabled = preferenceStore.get(BrightnessSettings.ENABLED),
            darkPercentage = preferenceStore.get(BrightnessSettings.DARK_PERCENT),
            darkThreshold = preferenceStore.get(BrightnessSettings.DARK_THRESHOLD),
            brightPercentage = preferenceStore.get(BrightnessSettings.BRIGHT_PERCENT),
            brightThreshold = preferenceStore.get(BrightnessSettings.BRIGHT_THRESHOLD)
        )
    }

    @Provides
    fun getGlareConfig(
        preferenceStore: PreferenceStore
    ): GlareConfig {
        return GlareConfig(
            enabled = preferenceStore.get(GlareSettings.ENABLED),
            threshold = preferenceStore.get(GlareSettings.THRESHOLD),
            varianceMin = preferenceStore.get(GlareSettings.VARIANCE_MIN),
            varianceMax = preferenceStore.get(GlareSettings.VARIANCE_MAX),
            maxGlareValue = preferenceStore.get(GlareSettings.MAX_GLARE_VALUE),
        )
    }
}