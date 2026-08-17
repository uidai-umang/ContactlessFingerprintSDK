package app.gov.uidai.registration.di

import android.content.Context
import app.gov.uidai.registration.data.remote.api.ClfApiService
import app.gov.uidai.registration.data.remote.network.RetrofitClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient = RetrofitClient.buildOkHttpClient(context)

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit = RetrofitClient.buildRetrofit(okHttpClient)

    @Provides
    @Singleton
    fun provideClfApiService(
        retrofit: Retrofit
    ): ClfApiService = RetrofitClient.buildApiService(retrofit)
}