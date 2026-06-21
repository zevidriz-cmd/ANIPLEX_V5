package com.aniplex.app.ui.main

import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class DetailCompareTest {
    @Test
    fun compareDetails() {
        val ids = listOf("6245", "6467")
        for (id in ids) {
            val urlString = "https://aniplex-proxy.f1886391.workers.dev/api/v2/anime/$id"
            println("=== Querying details for '$id' ===")
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.setRequestProperty("Accept", "application/json")
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    response.append(line)
                    line = reader.readLine()
                }
                reader.close()
                println("Response for $id: ${response.toString().take(300)}...")
            } catch (e: Exception) {
                println("Error querying $id: ${e.message}")
            }
        }
    }

    @Test
    fun testAniListSchedulesApi() {
        val testDate = "2026-06-13"
        println("=== Fetching AniList Schedules for: $testDate ===")
        try {
            val sdfStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsedDate = sdfStr.parse(testDate) ?: return
            val startSec = parsedDate.time / 1000L
            val endSec = startSec + (24 * 60 * 60) - 1

            val query = """
                query (${'$'}start: Int, ${'$'}end: Int) {
                  Page(page: 1, perPage: 50) {
                    airingSchedules(airingAt_greater: ${'$'}start, airingAt_lesser: ${'$'}end, sort: TIME) {
                      id
                      episode
                      airingAt
                      media {
                        id
                        idMal
                        title {
                          english
                          romaji
                          userPreferred
                        }
                        coverImage {
                          extraLarge
                          large
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            // Construct proper JSON payload for GraphQL
            val variables = mapOf("start" to startSec, "end" to endSec)
            val payload = mapOf("query" to query, "variables" to variables)
            val jsonPayload = com.google.gson.Gson().toJson(payload)

            val url = URL("https://graphql.anilist.co")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            connection.outputStream.use { os ->
                val input = jsonPayload.toByteArray(charset("utf-8"))
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            println("Response Code: $responseCode")
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    response.append(line)
                    line = reader.readLine()
                }
                reader.close()
                println("Response content: ${response.toString().take(1500)}")
            } else {
                val reader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val response = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    response.append(line)
                    line = reader.readLine()
                }
                reader.close()
                println("Error content: $response")
            }
        } catch (e: Exception) {
            println("Exception querying AniList: $e")
            e.printStackTrace()
        }
    }
}
