package com.onelist.tv.data

import com.google.gson.annotations.SerializedName

// ---- 登录请求 ----
data class LoginRequest(
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("user_password") val userPassword: String
)

// ---- 通用响应 ----
data class ApiResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: T?
)

data class ApiListResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: List<T>?,
    @SerializedName("num") val num: Int?
)

data class MovieDetailResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: Movie?,
    @SerializedName("like") val like: List<Movie>?
)

data class TvDetailResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: Tv?,
    @SerializedName("like") val like: List<Tv>?
)

data class LoginResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val token: String?,
    @SerializedName("user") val user: User?
)

data class HomeData(
    @SerializedName("galleries") val galleries: List<Gallery>?,
    @SerializedName("latest_movies") val latestMovies: List<Movie>?,
    @SerializedName("latest_tvs") val latestTvs: List<Tv>?,
    @SerializedName("gallery_items") val galleryItems: List<GalleryItem>?
)

// ---- 业务模型 ----
data class User(
    @SerializedName("id") val id: Int?,
    @SerializedName("user_name") val userName: String?,
    @SerializedName("user_email") val userEmail: String?,
    @SerializedName("is_admin") val isAdmin: Boolean?
)

data class Gallery(
    @SerializedName("id") val id: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("gallery_type") val galleryType: String?,
    @SerializedName("is_tv") val isTv: Boolean?,
    @SerializedName("gallery_uid") val galleryUid: String?,
    @SerializedName("image") val image: String?,
    @SerializedName("is_alist") val isAlist: Boolean?
)

// gallery_items 的值可能是 Movie 或 Tv，用通用类型接收
data class GalleryItem(
    @SerializedName("id") val id: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("gallery_uid") val galleryUid: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("overview") val overview: String?
) {
    val displayTitle: String get() = title ?: name ?: ""
    val isMovie: Boolean get() = title != null
    val year: String get() = (releaseDate ?: firstAirDate ?: "").take(4)
}

data class Genre(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?
)

data class Movie(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("gallery_uid") val galleryUid: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("genres") val genres: List<Genre>? = null,
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("star") val star: Boolean? = null,
    @SerializedName("heart") val heart: Boolean? = null,
    @SerializedName("played") val played: Boolean? = null
) {
    val poster: String? get() = posterPath
    val desc: String? get() = overview
    val year: String? get() = releaseDate?.take(4)
    val score: Double? get() = voteAverage
}

data class Tv(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("gallery_uid") val galleryUid: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("genres") val genres: List<Genre>? = null,
    @SerializedName("seasons") val seasons: List<Season>? = null,
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("star") val star: Boolean? = null,
    @SerializedName("heart") val heart: Boolean? = null,
    @SerializedName("played") val played: Boolean? = null
) {
    val poster: String? get() = posterPath
    val desc: String? get() = overview
    val title: String? get() = name
    val year: String? get() = firstAirDate?.take(4)
}

data class Season(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("episode_count") val episodeCount: Int? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("air_date") val airDate: String? = null
)

data class SeasonDetail(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("episodes") val episodes: List<Episode>? = null
)

data class Episode(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("episode_number") val episodeNumber: Int? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("gallery_uid") val galleryUid: String? = null
) {
    val title: String? get() = name
}

data class Config(
    @SerializedName("title") val title: String?,
    @SerializedName("img_url") val imgUrl: String?
)
