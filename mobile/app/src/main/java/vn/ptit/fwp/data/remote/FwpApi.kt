package vn.ptit.fwp.data.remote

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import vn.ptit.fwp.BuildConfig
import vn.ptit.fwp.data.model.CandidateCard
import vn.ptit.fwp.data.model.MatchState
import vn.ptit.fwp.data.model.MatchSummary
import vn.ptit.fwp.data.model.MeResponse
import vn.ptit.fwp.data.model.Message
import vn.ptit.fwp.data.model.SafetyRequest
import vn.ptit.fwp.data.model.SafetyScore
import vn.ptit.fwp.data.model.SendRequest
import vn.ptit.fwp.data.model.SwipeRequest
import vn.ptit.fwp.data.model.SwipeResponse
import java.util.concurrent.TimeUnit

/** Các endpoint của server FastAPI (thư mục serving/ ở gốc repo). */
interface FwpApi {
    @GET("app/me")
    suspend fun me(): MeResponse

    @GET("app/candidates")
    suspend fun candidates(): List<CandidateCard>

    @POST("app/swipe")
    suspend fun swipe(@Body req: SwipeRequest): SwipeResponse

    @GET("app/matches")
    suspend fun matches(): List<MatchSummary>

    @GET("app/matches/{id}/messages")
    suspend fun messages(@Path("id") matchId: String): List<Message>

    @POST("app/matches/{id}/messages")
    suspend fun send(@Path("id") matchId: String, @Body req: SendRequest): List<Message>

    @GET("app/matches/{id}/state")
    suspend fun state(@Path("id") matchId: String): MatchState

    @POST("app/reset")
    suspend fun reset(): Map<String, String>

    @POST("safety/score")
    suspend fun safety(@Body req: SafetyRequest): SafetyScore
}

object ApiClient {
    fun create(baseUrl: String = BuildConfig.AI_BASE_URL): FwpApi {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            })
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(FwpApi::class.java)
    }
}
