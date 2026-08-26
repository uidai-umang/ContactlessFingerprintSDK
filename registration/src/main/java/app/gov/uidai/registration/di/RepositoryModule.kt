package app.gov.uidai.registration.di

import app.gov.uidai.registration.repository.ClfRepository
import app.gov.uidai.registration.repository.DashboardRepository
import app.gov.uidai.registration.repository.FileRepository
import app.gov.uidai.registration.repository.FingerprintRepository
import app.gov.uidai.registration.repository.UserRepository
import app.gov.uidai.registration.repository.impl.ClfRepositoryImpl
import app.gov.uidai.registration.repository.impl.DashboardRepositoryImpl
import app.gov.uidai.registration.repository.impl.FileRepositoryImpl
import app.gov.uidai.registration.repository.impl.FingerprintRepositoryImpl
import app.gov.uidai.registration.repository.impl.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository
    
    @Binds
    @Singleton
    abstract fun bindFingerprintRepository(
        fingerprintRepositoryImpl: FingerprintRepositoryImpl
    ): FingerprintRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(
        fileRepositoryImpl: FileRepositoryImpl
    ): FileRepository

    @Binds
    @Singleton
    abstract fun bindClfRepository(
        impl: ClfRepositoryImpl
    ): ClfRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(
        impl: DashboardRepositoryImpl
    ): DashboardRepository
}
