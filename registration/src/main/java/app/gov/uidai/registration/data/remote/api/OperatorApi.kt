package app.gov.uidai.registration.data.remote.api

import app.gov.uidai.registration.model.operator.RegisterOperatorRequest
import app.gov.uidai.registration.model.operator.RegisterOperatorResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OperatorApi {

    @POST(Urls.OPERATOR_LOOKUP)
    suspend fun registerOperator(
        @Body request: RegisterOperatorRequest
    ): Response<RegisterOperatorResponse>
}
