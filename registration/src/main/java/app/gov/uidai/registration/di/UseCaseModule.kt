package app.gov.uidai.registration.di

import android.content.Context
import app.gov.uidai.registration.data.dao.PendingCaptureDao
import app.gov.uidai.registration.repository.ClfRepository
import app.gov.uidai.registration.repository.DashboardRepository
import app.gov.uidai.registration.repository.FingerprintRepository
import app.gov.uidai.registration.repository.UserRepository
import app.gov.uidai.registration.usecase.CaptureQueueManager
import app.gov.uidai.registration.usecase.CaptureUseCase
import app.gov.uidai.registration.usecase.DashboardUseCase
import app.gov.uidai.registration.usecase.DeviceUseCase
import app.gov.uidai.registration.usecase.FingerSDKManager
import app.gov.uidai.registration.usecase.ResidentUseCase
import app.gov.uidai.registration.usecase.SessionUseCase
import app.gov.uidai.registration.usecase.UIDManager
import app.gov.uidai.registration.usecase.UserUseCase
import app.gov.uidai.registration.usecase.impl.CaptureFileStorage
import app.gov.uidai.registration.usecase.impl.CaptureQueueManagerImpl
import app.gov.uidai.registration.usecase.impl.CaptureUseCaseImpl
import app.gov.uidai.registration.usecase.impl.DashboardUseCaseImpl
import app.gov.uidai.registration.usecase.impl.DeviceUseCaseImpl
import app.gov.uidai.registration.usecase.impl.FingerSDKManagerImpl
import app.gov.uidai.registration.usecase.impl.ResidentUseCaseImpl
import app.gov.uidai.registration.usecase.impl.SessionUseCaseImpl
import app.gov.uidai.registration.usecase.impl.UIDManagerImpl
import app.gov.uidai.registration.usecase.impl.UserUseCaseImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.gov.uidai.embedding.FingerEmbedder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Singleton
    @Provides
    fun provideUserUseCase(
        userRepository: UserRepository,
        fingerprintRepository: FingerprintRepository
    ): UserUseCase {
        return UserUseCaseImpl(
            userRepository = userRepository,
            fingerprintRepository = fingerprintRepository
        )
    }

    @Singleton
    @Provides
    fun provideFingerSDKManager(
        @ApplicationContext context: Context,
        fingerEmbedder: FingerEmbedder
    ): FingerSDKManager {
        return FingerSDKManagerImpl(
            context = context,
            fingerEmbedder =  fingerEmbedder
        )
    }

    @Provides
    @Singleton
    fun provideUIDManager(): UIDManager {
        return UIDManagerImpl()
    }

    @Provides
    @Singleton
    fun provideResidentUseCase(
        clfRepository: ClfRepository
    ): ResidentUseCase = ResidentUseCaseImpl(
        clfRepository = clfRepository
    )

    @Provides
    @Singleton
    fun provideSessionUseCase(
        clfRepository: ClfRepository
    ): SessionUseCase = SessionUseCaseImpl(
        clfRepository = clfRepository
    )

    @Provides
    @Singleton
    fun provideCaptureUseCase(
        clfRepository: ClfRepository
    ): CaptureUseCase = CaptureUseCaseImpl(
        clfRepository = clfRepository
    )

    @Provides
    @Singleton
    fun provideCaptureQueueManager(
        captureUseCase: CaptureUseCase,
        pendingCaptureDao: PendingCaptureDao,
        captureFileStorage: CaptureFileStorage
    ): CaptureQueueManager = CaptureQueueManagerImpl(
        captureUseCase = captureUseCase,
        pendingCaptureDao = pendingCaptureDao,
        captureFileStorage = captureFileStorage
    )

    @Provides
    @Singleton
    fun provideDeviceUseCase(
        clfRepository: ClfRepository
    ): DeviceUseCase = DeviceUseCaseImpl(
        clfRepository = clfRepository
    )

    @Provides
    @Singleton
    fun provideDashboardUseCase(
        dashboardRepository: DashboardRepository
    ): DashboardUseCase = DashboardUseCaseImpl(
        dashboardRepository = dashboardRepository
    )
}