package com.onelist.tv.data

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.*

interface ApiService {

    // 登录
    @POST("v1/api/user/login")
    fun login(
        @Body body: LoginRequest
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

    // 获取媒体库播放模式（AlistHost 非空=alist代理；空=本地直链；is_ali_open=阿里云盘open）
    @POST("v1/api/gallery/host")
    fun getGalleryHost(
        @Query("id") galleryId: String
    ): Call<GalleryHostResponse>

    // 阿里云盘 open 获取多清晰度直链
    @POST("v1/api/aliopen/video")
    fun getAliOpenVideo(
        @Body body: AliOpenVideoRequest
    ): Call<AliOpenVideoResponse>

    // 获取同目录视频文件列表（用于列表连续播放）
    @GET("v1/api/playlist")
    fun getPlaylist(
        @Query("gallery_uid") galleryUid: String,
        @Query("url") url: String
    ): Call<PlaylistResponse>

    // ==================== 消息中心 API ====================
    
    // 获取我的未读消息
    @GET("v1/api/message/mine")
    fun getMyMessages(): Call<ApiResponse<List<Message>>>

    // 标记消息为已读
    @POST("v1/api/message/read")
    fun readMessage(
        @Query("id") id: Int
    ): Call<ApiResponse<Void>>

    // 标记所有消息为已读
    @POST("v1/api/message/read-all")
    fun readAllMessages(): Call<ApiResponse<Void>>

    // ==================== 播放统计 API ====================

    // 上报播放心跳
    @POST("v1/api/play-history/heartbeat")
    fun sendHeartbeat(
        @Body body: HeartbeatRequest
    ): Call<ApiResponse<PlayHistory>>

    // 获取今日播放时长
    @GET("v1/api/play-history/today")
    fun getTodayDuration(): Call<ApiResponse<Int>>

    // ==================== 最爱 API ====================

    // 获取最爱列表
    @POST("v1/api/heart/data/list")
    fun getHeartList(
        @Query("data_type") dataType: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 30
    ): Call<ApiListResponse<com.google.gson.JsonElement>>

    // 切换最爱状态
    @POST("v1/api/heart/renew")
    fun toggleHeart(
        @Body body: HeartToggleRequest
    ): Call<ApiResponse<Any>>

    // ==================== 已播放 API ====================

    // 获取已播放列表
    @POST("v1/api/played/data/list")
    fun getPlayedDataList(
        @Query("data_type") dataType: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 30
    ): Call<ApiListResponse<com.google.gson.JsonElement>>

    // 切换已播放状态
    @POST("v1/api/played/renew")
    fun togglePlayed(
        @Body body: HeartToggleRequest
    ): Call<ApiResponse<Any>>
}

// ==================== 消息中心数据模型 ====================

data class Message(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: String,
    @SerializedName("user_name") val userName: String?,
    @SerializedName("content") val content: String,
    @SerializedName("priority") val priority: String, // "normal" or "forced"
    @SerializedName("sender_type") val senderType: String,
    @SerializedName("sender_name") val senderName: String?,
    @SerializedName("read_at") val readAt: String?,
    @SerializedName("created_at") val createdAt: String
)

// ==================== 播放统计数据模型 ====================

data class HeartbeatRequest(
    @SerializedName("data_type") val dataType: String, // "movie" or "tv"
    @SerializedName("data_id") val dataId: Int,
    @SerializedName("title") val title: String,
    @SerializedName("gallery_uid") val galleryUid: String,
    @SerializedName("gallery_title") val galleryTitle: String,
    @SerializedName("duration") val duration: Int, // seconds since last heartbeat
    @SerializedName("position") val position: Int, // current playback position in seconds
    @SerializedName("total_duration") val totalDuration: Int // total video duration in seconds
)

data class PlayHistory(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: String,
    @SerializedName("data_type") val dataType: String,
    @SerializedName("data_id") val dataId: Int,
    @SerializedName("title") val title: String,
    @SerializedName("gallery_uid") val galleryUid: String,
    @SerializedName("gallery_title") val galleryTitle: String,
    @SerializedName("duration") val duration: Int,
    @SerializedName("position") val position: Int,
    @SerializedName("total_duration") val totalDuration: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class PlaylistResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: List<String>?
)

data class AliOpenVideoRequest(
    @SerializedName("file") val file: String,
    @SerializedName("gallery_uid") val galleryUid: String
)

data class AliOpenVideoResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: AliOpenVideoData?
)

data class AliOpenVideoData(
    @SerializedName("video_preview_play_info") val videoPreviewPlayInfo: VideoPreviewPlayInfo?
)

data class VideoPreviewPlayInfo(
    @SerializedName("live_transcoding_task_list") val liveTranscodingTaskList: List<TranscodingTask>?
)

data class TranscodingTask(
    @SerializedName("url") val url: String?,
    @SerializedName("template_id") val templateId: String?,
    @SerializedName("template_name") val templateName: String?,
    @SerializedName("file_id") val fileId: String?
)

data class GalleryHostResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val `data`: String?,
    @SerializedName("is_ali_open") val isAliOpen: Boolean?
)

data class GameFile(
    @com.google.gson.annotations.SerializedName("name") val name: String,
    @com.google.gson.annotations.SerializedName("file") val file: String,
    @com.google.gson.annotations.SerializedName("url") val url: String
)

data class HeartToggleRequest(
    @SerializedName("data_type") val dataType: String,
    @SerializedName("data_id") val dataId: Int
)
