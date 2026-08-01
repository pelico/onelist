package com.onelist.tv.data

import com.google.gson.annotations.SerializedName

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
    @SerializedName("id") val id: Int?,
    @SerializedName("gallery_uid") val galleryUid: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("runtime") val runtime: Int?,
    @SerializedName("url") val url: String?,
    @SerializedName("genres") val genres: List<Genre>?,
    @SerializedName("tagline") val tagline: String?,
    @SerializedName("star") val star: Boolean?,
    @SerializedName("heart") val heart: Boolean?,
    @SerializedName("played") val played: Boolean?
) {
    val poster: String? get() = posterPath
    val desc: String? get() = overview
    val year: String? get() = releaseDate?.take(4)
    val score: Double? get() = voteAverage
}

data class Tv(
    @SerializedName("id") val id: Int?,
    @SerializedName("gallery_uid") val galleryUid: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("original_name") val originalName: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int?,
    @SerializedName("url") val url: String?,
    @SerializedName("genres") val genres: List<Genre>?,
    @SerializedName("seasons") val seasons: List<Season>?,
    @SerializedName("tagline") val tagline: String?,
    @SerializedName("star") val star: Boolean?,
    @SerializedName("heart") val heart: Boolean?,
    @SerializedName("played") val played: Boolean?
) {
    val poster: String? get() = posterPath
    val desc: String? get() = overview
    val title: String? get() = name
    val year: String? get() = firstAirDate?.take(4)
}

data class Season(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("episode_count") val episodeCount: Int?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("air_date") val airDate: String?
)

data class SeasonDetail(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("episodes") val episodes: List<Episode>?
)

data class Episode(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("episode_number") val episodeNumber: Int?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("still_path") val stillPath: String?,
    @SerializedName("runtime") val runtime: Int?,
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("gallery_uid") val galleryUid: String?
) {
    val title: String? get() = name
}

data class Config(
    @SerializedName("title") val title: String?,
    @SerializedName("img_url") val imgUrl: String?
)
