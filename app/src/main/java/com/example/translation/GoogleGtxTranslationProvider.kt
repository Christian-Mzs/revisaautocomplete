package com.example.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GoogleGtxTranslationProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
) : TranslationProvider {

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext TranslationResult.Success(
                translatedText = text,
                detectedSourceLanguage = sourceLanguage,
                httpStatusCode = 200,
                latencyMs = 0
            )
        }

        val startTime = System.currentTimeMillis()

        val baseUrl = "https://translate.googleapis.com/translate_a/single"
        val urlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: return@withContext TranslationResult.Error(
                errorMessage = "Não foi possível construir URL de tradução.",
                errorType = TranslationResult.ErrorType.UNKNOWN
            )

        urlBuilder.addQueryParameter("client", "gtx")
        urlBuilder.addQueryParameter("sl", sourceLanguage)
        urlBuilder.addQueryParameter("tl", targetLanguage)
        urlBuilder.addQueryParameter("dt", "t")
        urlBuilder.addQueryParameter("dj", "1")
        urlBuilder.addQueryParameter("q", text)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val code = response.code

                when {
                    code == 429 -> {
                        return@withContext TranslationResult.Error(
                            errorMessage = "Limite temporário do serviço. Tente novamente depois.",
                            httpStatusCode = code,
                            errorType = TranslationResult.ErrorType.RATE_LIMITED
                        )
                    }
                    code == 403 || code >= 500 -> {
                        return@withContext TranslationResult.Error(
                            errorMessage = "Serviço de correção indisponível.",
                            httpStatusCode = code,
                            errorType = TranslationResult.ErrorType.SERVICE_UNAVAILABLE
                        )
                    }
                    !response.isSuccessful -> {
                        return@withContext TranslationResult.Error(
                            errorMessage = "Não foi possível corrigir agora.",
                            httpStatusCode = code,
                            errorType = TranslationResult.ErrorType.UNKNOWN
                        )
                    }
                }

                val bodyString = response.body?.string().orEmpty()
                if (bodyString.isBlank()) {
                    return@withContext TranslationResult.Error(
                        errorMessage = "Resposta de tradução vazia.",
                        httpStatusCode = code,
                        errorType = TranslationResult.ErrorType.EMPTY_RESPONSE
                    )
                }

                try {
                    val json = JSONObject(bodyString)
                    val detectedSrc = json.optString("src", sourceLanguage)
                    val sentences = json.optJSONArray("sentences")

                    if (sentences == null || sentences.length() == 0) {
                        return@withContext TranslationResult.Error(
                            errorMessage = "Não foi possível extrair a frase traduzida.",
                            httpStatusCode = code,
                            errorType = TranslationResult.ErrorType.EMPTY_RESPONSE
                        )
                    }

                    val sb = StringBuilder()
                    for (i in 0 until sentences.length()) {
                        val sentenceObj = sentences.optJSONObject(i)
                        val trans = sentenceObj?.optString("trans").orEmpty()
                        sb.append(trans)
                    }

                    val translatedText = sb.toString()
                    return@withContext TranslationResult.Success(
                        translatedText = translatedText,
                        detectedSourceLanguage = detectedSrc,
                        httpStatusCode = code,
                        latencyMs = latency
                    )
                } catch (e: Exception) {
                    return@withContext TranslationResult.Error(
                        errorMessage = "Falha ao processar resposta do tradutor.",
                        httpStatusCode = code,
                        errorType = TranslationResult.ErrorType.UNKNOWN
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            return@withContext TranslationResult.Error(
                errorMessage = "Não foi possível corrigir agora.",
                errorType = TranslationResult.ErrorType.TIMEOUT
            )
        } catch (e: UnknownHostException) {
            return@withContext TranslationResult.Error(
                errorMessage = "Sem conexão para corrigir.",
                errorType = TranslationResult.ErrorType.NO_CONNECTION
            )
        } catch (e: ConnectException) {
            return@withContext TranslationResult.Error(
                errorMessage = "Sem conexão para corrigir.",
                errorType = TranslationResult.ErrorType.NO_CONNECTION
            )
        } catch (e: IOException) {
            return@withContext TranslationResult.Error(
                errorMessage = "Não foi possível corrigir agora.",
                errorType = TranslationResult.ErrorType.UNKNOWN
            )
        } catch (e: Exception) {
            return@withContext TranslationResult.Error(
                errorMessage = "Erro inesperado ao corrigir.",
                errorType = TranslationResult.ErrorType.UNKNOWN
            )
        }
    }
}
