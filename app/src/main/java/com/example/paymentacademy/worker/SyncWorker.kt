package com.example.paymentacademy.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.paymentacademy.data.remote.SupabaseProvider
import com.example.paymentacademy.data.repository.SupabaseMemberRepository
import com.example.paymentacademy.data.repository.SupabasePaymentRepository
import io.github.jan.supabase.auth.auth

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Garantizar que la sesión esté cargada antes de interactuar en segundo plano
            SupabaseProvider.client.auth.awaitInitialization()

            val memberRepo = SupabaseMemberRepository(SupabaseProvider.client, applicationContext)
            val paymentRepo = SupabasePaymentRepository(SupabaseProvider.client, applicationContext)

            memberRepo.sync()
            paymentRepo.sync()

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Error de sincronización en segundo plano", e)
            // Si no hay conexión o falla el servidor, WorkManager reintentará más tarde
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "PaymentAcademySync",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
