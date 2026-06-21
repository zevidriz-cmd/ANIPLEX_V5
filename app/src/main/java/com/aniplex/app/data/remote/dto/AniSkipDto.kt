package com.aniplex.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AniSkipResponse(
    @SerializedName("found") val found: Boolean,
    @SerializedName("results") val results: List<AniSkipResultDto>?,
    @SerializedName("message") val message: String?,
    @SerializedName("statusCode") val statusCode: Int
)

data class AniSkipResultDto(
    @SerializedName("interval") val interval: AniSkipIntervalDto,
    @SerializedName("skipType") val skipType: String,
    @SerializedName("skipId") val skipId: String,
    @SerializedName("episodeLength") val episodeLength: Double
)

data class AniSkipIntervalDto(
    @SerializedName("startTime") val startTime: Double,
    @SerializedName("endTime") val endTime: Double
)
