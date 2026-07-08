package com.aniplex.app.di

import android.content.Context
import androidx.room.Room
import com.aniplex.app.data.local.dao.CacheDao
import com.aniplex.app.data.local.dao.DownloadDao
import com.aniplex.app.data.local.database.AppDatabase
import com.aniplex.app.data.remote.api.HiAnimeApiService
import com.aniplex.app.data.remote.api.FallbackApiService
import com.aniplex.app.data.repository.AnimeRepositoryImpl
import com.aniplex.app.data.repository.AuthRepositoryImpl
import com.aniplex.app.data.local.preferences.PreferenceManager
import com.aniplex.app.domain.repository.AnimeRepository
import com.aniplex.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Upstream fallback API base URL (now pointing to our Cloudflare Jikan proxy)
    private const val BASE_URL = "https://aniplex-proxy.f1886391.workers.dev/"
    private const val NETLIFY_BASE_URL = "https://anistream-web.netlify.app/"

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (com.aniplex.app.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideHiAnimeApiService(okHttpClient: OkHttpClient): HiAnimeApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HiAnimeApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideFallbackApiService(okHttpClient: OkHttpClient): FallbackApiService {
        return Retrofit.Builder()
            .baseUrl(NETLIFY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FallbackApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAniSkipApiService(okHttpClient: OkHttpClient): com.aniplex.app.data.remote.api.AniSkipApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.aniskip.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.aniplex.app.data.remote.api.AniSkipApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "aniplex_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    @Provides
    @Singleton
    fun provideCacheDao(database: AppDatabase): CacheDao {
        return database.cacheDao()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun provideAnimeRepository(
        apiService: HiAnimeApiService,
        fallbackApiService: FallbackApiService,
        cacheDao: CacheDao,
        gson: Gson,
        okHttpClient: OkHttpClient,
        preferenceManager: PreferenceManager,
        aniSkipApiService: com.aniplex.app.data.remote.api.AniSkipApiService
    ): AnimeRepository {
        return AnimeRepositoryImpl(apiService, fallbackApiService, cacheDao, gson, okHttpClient, preferenceManager, aniSkipApiService)
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): AuthRepository {
        return AuthRepositoryImpl(firebaseAuth, firestore)
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .crossfade(300)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.03)
                    .build()
            }
            .build()
    }
}
