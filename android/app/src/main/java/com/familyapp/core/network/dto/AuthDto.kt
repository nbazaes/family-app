package com.familyapp.core.network.dto

import com.google.gson.annotations.SerializedName

data class ConnectRequestDto(
    @SerializedName("member_name") val memberName: String = "Familiar",
    @SerializedName("family_code") val familyCode: String? = null
)

data class LoginRequestDto(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RegisterRequestDto(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("family_name") val familyName: String? = "Familia"
)

data class TokenResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("family_id") val familyId: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String? = null
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("family_id") val familyId: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String
)
