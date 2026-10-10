package app.gov.uidai.registration.repository.impl

import app.gov.uidai.registration.data.remote.api.OperatorApi
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.data.remote.network.ResponseHandler
import app.gov.uidai.registration.model.operator.RegisterOperatorRequest
import app.gov.uidai.registration.model.operator.RegisterOperatorResponse
import app.gov.uidai.registration.pref.PreferenceStore
import app.gov.uidai.registration.pref.model.PreferenceParam
import app.gov.uidai.registration.pref.model.PreferenceType
import app.gov.uidai.registration.repository.OperatorRepository
import javax.inject.Inject

class OperatorRepositoryImpl @Inject constructor(
    private val operatorApi: OperatorApi,
    private val preferenceStore: PreferenceStore
) : OperatorRepository {

    companion object {
        private val OPERATOR_REF_ID_PREF = PreferenceParam(
            key = "operator.ref_id",
            displayName = "Operator Ref ID",
            type = PreferenceType.STRING,
            defaultValue = ""
        )

        private val OPERATOR_ID_PREF = PreferenceParam(
            key = "operator.id",
            displayName = "Operator ID",
            type = PreferenceType.STRING,
            defaultValue = ""
        )
    }

    override suspend fun registerOperator(
        operatorRefId: String
    ): ApiResult<RegisterOperatorResponse> {
        val result = ResponseHandler.safeApiCall {
            operatorApi.registerOperator(RegisterOperatorRequest(operatorRefId))
        }
        if (result is ApiResult.Success) {
            preferenceStore.save(OPERATOR_REF_ID_PREF.copy(currentValue = operatorRefId))
            preferenceStore.save(OPERATOR_ID_PREF.copy(currentValue = result.data.operatorId))
        }
        return result
    }

    override fun getLastOperatorRefId(): String = preferenceStore.get(OPERATOR_REF_ID_PREF)

    override fun getOperatorId(): String = preferenceStore.get(OPERATOR_ID_PREF)
}
