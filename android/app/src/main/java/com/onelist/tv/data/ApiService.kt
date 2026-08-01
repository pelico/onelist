package com.onelist.tv.data

import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    // 登录
    @FormUrlEncoded
    @POST("v1/api/user/login")
    fun login(
        @Field("user_email") email: String,
        @Field("user_password") password: String
    ): Call<LoginResponse>

    // 首页数据
    @GET("v1/api/home?size=30&gallery_size=30")
    fun getHome(): Call<ApiResponse<HomeData>>

    // 电影列表 (POST with empty body, params in query string)
    @POST("v1/api/themovie/list")
    fun getMovieList(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 30
    ): Call<ApiListResponse<Movie>>

    // 电视列表
    @POST("v1/api/thetv/list")
    fun getTvList(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 30
    ): Call<ApiListResponse<Tv>>

    // 电影库筛选
    @POST("v1/api/themovie/gallery/list")
    fun getMovieListByGallery(
        @Query("id") galleryId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 30
    ): Call<ApiListResponse<Movie>>

    // 电视库筛选
    @POST("v1/api/thetv/gallery/list")
    fun getTvListByGallery(
        @Query("id") galleryId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 30
    ): Call<ApiListResponse<Tv>>

    // 电影详情
    @POST("v1/api/themovie/id")
    fun getMovieDetail(
        @Query("id") id: String
    ): Call<MovieDetailResponse>

    // 电视详情
    @POST("v1/api/thetv/id")
    fun getTvDetail(
        @Query("id") id: String
    ): Call<TvDetailResponse>

    // 季详情
    @POST("v1/api/theseason/id")
    fun getSeasonDetail(
        @Query("id") id: String
    ): Call<ApiResponse<SeasonDetail>>

    // 电影搜索
    @POST("v1/api/themovie/search")
    fun searchMovie(
        @Query("q") query: String,
        @Query("size") size: Int = 20
    ): Call<ApiResponse<List<Movie>>>

    // 电视搜索
    @POST("v1/api/thetv/search")
    fun searchTv(
        @Query("q") query: String,
        @Query("size") size: Int = 20
    ): Call<ApiResponse<List<Tv>>>

    // 配置
    @GET("v1/api/configs")
    fun getConfigs(): Call<ApiResponse<Config>>

    // 游戏列表
    @GET("v1/api/game/list")
    fun getGameList(): Call<ApiResponse<List<GameFile>>>
}

data class GameFile(
    @com.google.gson.annotations.SerializedName("name") val name: String,
    @com.google.gson.annotations.SerializedName("file") val file: String,
    @com.google.gson.annotations.SerializedName("url") val url: String
)
