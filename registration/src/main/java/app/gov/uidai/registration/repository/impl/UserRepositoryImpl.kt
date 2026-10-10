package app.gov.uidai.registration.repository.impl

import app.gov.uidai.registration.data.dao.UserDao
import app.gov.uidai.registration.data.entity.UserEntity
import app.gov.uidai.registration.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {

    override suspend fun getUserByUidHash(uidHash: String): UserEntity? {
        return userDao.getUserByUidHash(uidHash)
    }

    override suspend fun insertUser(user: UserEntity) {
        userDao.insertUser(user)
    }

    override suspend fun deleteUser(uidHash: String) {
        userDao.deleteUser(uidHash)
    }

    override suspend fun isUserRegistered(uidHash: String): Boolean {
        return userDao.isUserRegistered(uidHash)
    }
}