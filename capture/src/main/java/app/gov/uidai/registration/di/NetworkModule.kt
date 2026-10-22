package app.gov.uidai.registration.di

import android.content.Context
import app.gov.uidai.registration.auth.BearerTokenInterceptor
import app.gov.uidai.registration.auth.TokenAuthenticator
import app.gov.uidai.registration.data.remote.api.ClfApiService
import app.gov.uidai.registration.data.remote.api.OperatorApi
import app.gov.uidai.registration.data.remote.network.RetrofitClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Auth client: no bearer interceptor, no authenticator (avoids refresh loops).
    @Provides
    @Singleton
    @Named("authClient")
    fun provideAuthClient(
        @ApplicationContext context: Context
    ): OkHttpClient = RetrofitClient.buildOkHttpClient(context)

    @Provides
    @Singleton
    @Named("authRetrofit")
    fun provideAuthRetrofit(
        @Named("authClient") client: OkHttpClient
    ): Retrofit = RetrofitClient.buildRetrofit(client)

    @Provides
    @Singleton
    @Named("authApi")
    fun provideAuthApi(
        @Named("authRetrofit") retrofit: Retrofit
    ): ClfApiService = RetrofitClient.buildApiService(retrofit)

    // Main client: adds Bearer token, refreshes and retries on 401.
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        bearerTokenInterceptor: BearerTokenInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient =
        RetrofitClient.buildOkHttpClient(context, bearerTokenInterceptor, tokenAuthenticator)

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        RetrofitClient.buildRetrofit(okHttpClient)

    @Provides
    @Singleton
    fun provideClfApiService(retrofit: Retrofit): ClfApiService =
        RetrofitClient.buildApiService(retrofit)

    @Provides
    @Singleton
    fun provideOperatorApi(retrofit: Retrofit): OperatorApi =
        retrofit.create(OperatorApi::class.java)
}