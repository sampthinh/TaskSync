package com.example.tasksync.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Cac viec app nho server lam. Tra ve Response<> de doc duoc ma HTTP (200, 201, 409...). */
interface TaskApi {

    @POST("tasks")
    suspend fun create(@Body task: TaskDto): Response<TaskDto>

    @PUT("tasks/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateDto): Response<TaskDto>

    @DELETE("tasks/{id}")
    suspend fun delete(@Path("id") id: String): Response<TaskDto>

    @GET("tasks/changes")
    suspend fun changes(@Query("since") since: Long): Response<ChangesDto>
}
