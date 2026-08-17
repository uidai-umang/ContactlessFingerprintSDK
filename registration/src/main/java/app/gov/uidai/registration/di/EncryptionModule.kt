package app.gov.uidai.registration.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.gov.uidai.registration.encryption.Encrypter
import app.gov.uidai.registration.encryption.EncryptionService
import app.gov.uidai.registration.encryption.X509CertificateFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EncryptionModule {
    private const val ENCRYPTION_CERTIFICATE_PEM = """
        -----BEGIN CERTIFICATE-----
MIIDLTCCAhWgAwIBAgIUCMHFdUIOcqpvrwYr5yr5c2s5UtwwDQYJKoZIhvcNAQEL
BQAwJjEkMCIGA1UEAwwbY29udGFjdGxlc3MtZmluZ2VycHJpbnQtZGV2MB4XDTI2
MDgxNDA5NTIzNloXDTI3MDgxNDA5NTIzNlowJjEkMCIGA1UEAwwbY29udGFjdGxl
c3MtZmluZ2VycHJpbnQtZGV2MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC
AQEAr/zqEf/0abbnVcDKTVxObsoXolzboD2T0+Lbizd6JwLiDxHIStaqHIUoTFYy
42Ro4p5go1v3j4KwEmbas8cMlqS66sVqfQZW984PjYvcvpeGDzgmOWi0jg36LziA
ecu8AXQBzvhz4RBL7ZPuKAj2WGhWzDG9DOw4CEVcoXyahvCNlVQNHKuOqxzREv7W
L2KllRz5KC/F9hM/HUJhF7Bsp8s70UoNxFQNBeMDZ7QXGJUPZKz4DZ6OiMwN0qky
g75Dr3jOXho+VHe6nTnmV9A91hoFpxvfLc427D3jg76ezWrxsyN/A98YKT73wsGA
nRxmP/rboIUs+YecraDz3hjzXQIDAQABo1MwUTAdBgNVHQ4EFgQUZZnFcDQuohah
JqWD+aFLOKJrIdIwHwYDVR0jBBgwFoAUZZnFcDQuohahJqWD+aFLOKJrIdIwDwYD
VR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAFhC7fJgkL/4shKQe46Pz
CFR7dfWyAdJBholkg6qushLJzpMs3XtmdByl0XC029O3r/yf2/DXsRBENVZV9liF
VKpmGYp4xAiQdrG7eNlSI1jo6i5A7kS24mP7DMWvCnuYJ3gLfHCm8Zzi6y4IrX/r
ll8GmmYf3VQzj54DFnZrsRknssDjYwWrD+SGBdn8dZkFIUziF4uW6gmfrU5U6GG3
zE18lqG/7C7AJFLH2BhFWWihB6mNlpmS1BaClHrhYNmFal9k+414gINsN2Z2lrzx
sycMXBrM8UHLKnxwYcviXU+PF9qQnMabe8jjskf+XfTfgld2k6/HvSg1hMOssUFt
4g==
-----END CERTIFICATE-----
    """


    @Provides
    @Singleton
    fun provideX509CertificateFactory(): X509CertificateFactory = X509CertificateFactory()

    @Provides
    @Singleton
    fun provideEncrypter(
        certificateFactory: X509CertificateFactory
    ): Encrypter {
        val certificate = certificateFactory.generateCertificate(ENCRYPTION_CERTIFICATE_PEM)
        return Encrypter(certificate)
    }

    @Provides
    @Singleton
    fun provideEncryptionService(
        encrypter: Encrypter,
        certificateFactory: X509CertificateFactory
    ): EncryptionService {
        val certificate = certificateFactory.generateCertificate(ENCRYPTION_CERTIFICATE_PEM)
        return EncryptionService(encrypter, certificate)
    }
}