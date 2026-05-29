package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Payment
import com.example.fixbid.domain.model.PaymentStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.ServiceCategory
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.repository.WorkerRepository
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** One bar in the earnings chart — a calendar month and the amount earned. */
data class MonthlyEarning(
    val label: String,        // "T1", "T2", ...
    val amount: Double
)

/** Count of completed jobs per service category. */
data class CategoryStat(
    val category: ServiceCategory,
    val count: Int
)

data class WorkerAnalytics(
    val totalEarnings: Double,
    val thisMonthEarnings: Double,
    val lastMonthEarnings: Double,
    val completedJobs: Int,
    val averagePerJob: Double,
    val averageRating: Double,
    val totalReviews: Int,
    val acceptanceJobs: Int,             // jobs won (confirmed + in progress + completed)
    val monthlySeries: List<MonthlyEarning>,
    val categoryBreakdown: List<CategoryStat>
) {
    /** % change of this month vs last month. Null when no baseline. */
    val monthOverMonthGrowth: Double?
        get() = when {
            lastMonthEarnings <= 0.0 && thisMonthEarnings <= 0.0 -> null
            lastMonthEarnings <= 0.0 -> 100.0
            else -> ((thisMonthEarnings - lastMonthEarnings) / lastMonthEarnings) * 100.0
        }
}

/**
 * Aggregates worker productivity + earnings analytics from payment history and
 * completed bookings. Pure read; safe to call on screen open / refresh.
 */
class GetWorkerAnalyticsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val workerRepository: WorkerRepository
) {
    suspend operator fun invoke(): Resource<WorkerAnalytics> {
        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")

        val profile = (workerRepository.getWorkerById(user.id) as? Resource.Success)?.data

        val payments = (paymentRepository.getPaymentHistory(user.id) as? Resource.Success)
            ?.data
            ?.filter { it.status == PaymentStatus.COMPLETED }
            ?: emptyList()

        val completedJobs = (bookingRepository.getWorkerBookings(user.id, BookingStatus.COMPLETED) as? Resource.Success)
            ?.data ?: emptyList()

        val zone = ZoneId.systemDefault()
        val thisMonth = YearMonth.now(zone)
        val lastMonth = thisMonth.minusMonths(1)

        fun Payment.month(): YearMonth {
            val ts = paidAt ?: releasedAt ?: createdAt
            return YearMonth.from(Instant.ofEpochMilli(ts).atZone(zone))
        }

        val totalEarnings = payments.sumOf { it.workerReceives }
        val thisMonthEarnings = payments.filter { it.month() == thisMonth }.sumOf { it.workerReceives }
        val lastMonthEarnings = payments.filter { it.month() == lastMonth }.sumOf { it.workerReceives }

        // Last 6 months earnings series (oldest → newest).
        val monthlySeries = (5 downTo 0).map { back ->
            val ym = thisMonth.minusMonths(back.toLong())
            val amount = payments.filter { it.month() == ym }.sumOf { it.workerReceives }
            MonthlyEarning(label = "T${ym.monthValue}", amount = amount)
        }

        val completedCount = completedJobs.size.coerceAtLeast(0)
            .let { if (it == 0) (profile?.totalJobsDone ?: 0) else it }
        val averagePerJob = if (payments.isNotEmpty()) totalEarnings / payments.size else 0.0

        val categoryBreakdown = completedJobs
            .groupingBy { it.category }
            .eachCount()
            .map { (category, count) -> CategoryStat(category, count) }
            .sortedByDescending { it.count }

        return Resource.Success(
            WorkerAnalytics(
                totalEarnings = totalEarnings,
                thisMonthEarnings = thisMonthEarnings,
                lastMonthEarnings = lastMonthEarnings,
                completedJobs = completedCount,
                averagePerJob = averagePerJob,
                averageRating = profile?.averageRating ?: 0.0,
                totalReviews = profile?.totalReviews ?: 0,
                acceptanceJobs = payments.size,
                monthlySeries = monthlySeries,
                categoryBreakdown = categoryBreakdown
            )
        )
    }
}
