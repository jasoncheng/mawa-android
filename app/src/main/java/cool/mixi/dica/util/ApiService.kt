package cool.mixi.dica.util

import com.google.gson.GsonBuilder
import cool.mixi.dica.App
import cool.mixi.dica.BuildConfig
import cool.mixi.dica.bean.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit

interface ApiService {

    @POST("statuses/update")
    @FormUrlEncoded
    fun statusUpdate(
        @Field("source") source: String,
        @Field("status") status: String,
        @Field("in_reply_to_status_id") in_reply_to_status_id: Int,
        @Field("lat") lat: String,
        @Field("long") long: String,
        @Field("group_allow[]") group_allow: ArrayList<Int>?): Call<Status>

    @POST("statuses/update_with_media")
    @Multipart
    fun statusUpdate(
        @Part("source") source: RequestBody,
        @Part("status") status: RequestBody,
        @Part("in_reply_to_status_id") in_reply_to_status_id: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lat") long: RequestBody,
        @PartMap group_allow: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part image: MultipartBody.Part): Call<Status>

    @GET("statuses/show?include_entities=true")
    fun statusShow(@Query("id") id: Int,
                   @Query("conversation") conversation: Int): Call<List<Status>>

    @GET("statuses/public_timeline?exclude_replies=true")
    fun statusPublicTimeline(@Query("since_id") since_id: String,
                             @Query("max_id") max_id: String): Call<List<Status>>

    @GET("statuses/friends_timeline?exclude_replies=true")
    fun statusFriendsTimeline(@Query("since_id") since_id: String,
                              @Query("max_id") max_id: String): Call<List<Status>>

    @GET("statuses/user_timeline?exclude_replies=true")
    fun statusUserTimeline(
        @Query("user_id") user_id: Int,
        @Query("since_id") since_id: String,
        @Query("max_id") max_id: String): Call<List<Status>>

    @GET("friendica/profile/show")
    fun friendicaProfileShow(@Query("profile_id") profile_id: String?): Call<Profile>

    @GET("users/show")
    fun usersShow(@Query("user_id") user_id: String): Call<User>

    @POST("friendica/activity/like")
    @FormUrlEncoded
    fun like(@Field("id") id: Int): Call<String>

    @POST("friendica/activity/unlike")
    @FormUrlEncoded
    fun unlike(@Field("id") id: Int): Call<String>

    @GET("friendica/group_show")
    fun friendicaGroupShow(): Call<ArrayList<Group>>

    @GET("friendica/notifications")
    fun friendicaNotifications(): Call<List<Notification>>

    @POST("friendica/notification/seen")
    @FormUrlEncoded
    fun friendicaNotificationSeen(@Field("id") nid: Int): Call<String>

    @GET("favoritesTimeline")
    fun favoritesTimeline(@Query("since_id") since_id: String,
                          @Query("max_id") max_id: String): Call<List<Status>>

    @POST("favorites/create")
    @FormUrlEncoded
    fun favoritesCreate(@Field("id") id: Int): Call<Status>

    @POST("favorites/destroy")
    @FormUrlEncoded
    fun favoritesDestroy(@Field("id") id: Int): Call<Status>

    companion object Factory {

        var cookies = ArrayList<String>()
        var sessionCookie:String? = null

        private val client: OkHttpClient
            get() {
                val clientBuilder = OkHttpClient.Builder()

                if(BuildConfig.DEBUG) {
                    val interceptor = HttpLoggingInterceptor()
                    interceptor.level = HttpLoggingInterceptor.Level.HEADERS
                    clientBuilder.addInterceptor(interceptor)
                }

                val authToken = Credentials.basic(
                    PrefUtil.getUsername(),
                    PrefUtil.getPassword()
                )

                val basicAuthInterceptor = Interceptor {
                    var request = it.request()
                    val headers = request.headers().newBuilder().add("Authorization", authToken).build()
                    request = request.newBuilder().headers(headers).build()
                    it.proceed(request)
                }

                val cacheInterceptor = Interceptor {
                    val request = if(NetworkUtil.isNetworkConnected()) {
                        it.request().newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
                    } else {
                        it.request().newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
                    }

                    it.proceed(request)
                }

                val saveCookieInterceptor = Interceptor {
                    val res = it.proceed(it.request())
                    res.headers().toMultimap().forEach { (key, value) ->
                        var thisCookie = value[0].split(";".toRegex())[0]
                        if(key.toLowerCase() == "set-cookie" && thisCookie.contains("PHPSESSID")){
                            sessionCookie = thisCookie
                        }
                    }

                    res
                }

                val addCookieInterceptor = Interceptor { it ->
                    val builder = it.request().newBuilder()!!
                    if(sessionCookie != null){

                    }
                    sessionCookie?.let {
                        dLog("Request Header reuse w/ Cookie Cache $it")
                        builder.addHeader("Cookie", it)
                    }

                    it.proceed(builder.build())
                }

                val cacheSize = Consts.CACHE_SIZE_IN_MB * 1024 * 1024
                val cache = Cache(File(App.instance.cacheDir, "http-cache"), cacheSize.toLong())


                return clientBuilder.cache(cache)
                    .addInterceptor(basicAuthInterceptor)
                    .addInterceptor(addCookieInterceptor)
                    .addInterceptor(saveCookieInterceptor)
                    .addInterceptor(cacheInterceptor)
                    .connectTimeout(Consts.API_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(Consts.API_READ_TIMEOUT, TimeUnit.SECONDS)
                    .build()
            }

        fun create(): ApiService {
            val gson = GsonBuilder().serializeNulls().setLenient().create()
            val retrofit = Retrofit.Builder()
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .baseUrl("${PrefUtil.getApiUrl()}/api/")
                .client(client)
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}
