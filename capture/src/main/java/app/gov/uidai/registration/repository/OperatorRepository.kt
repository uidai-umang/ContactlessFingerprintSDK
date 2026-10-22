package app.gov.uidai.registration.repository

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.operator.RegisterOperatorResponse

interface OperatorRepository {

    suspend fun registerOperator(operatorRefId: String): ApiResult<RegisterOperatorResponse>

    fun getLastOperatorRefId(): String

    // operator_id returned by the backend at the last successful registration.
    fun getOperatorId(): String
}
