package com.example.fixbid.domain.usecase.worker

import com.example.fixbid.domain.model.Booking
import com.example.fixbid.domain.model.BookingStatus
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.WorkerProfile
import com.example.fixbid.domain.repository.AuthRepository
import com.example.fixbid.domain.repository.BookingRepository
import com.example.fixbid.domain.repository.PaymentRepository
import com.example.fixbid.domain.repository.WorkerRepository
import javax.inject.Inject

data class WorkerDashboardData(
    val profile: WorkerProfile?,
    val activeJobs: List<Booking>,
    val pendingJobs: List<Booking>,
    val completedJobs: List<Booking>,
    /**
     * Bookings the worker has cancelled (status = CANCELLED). Surfaced in the
     * "Việc làm của tôi" screen so the worker can review prior cancellations
     * along with the reason and refund amount.
     */
    val cancelledJobs: List<Booking> = emptyList(),
    /**
     * Direct bookings the customer assigned to this worker that are still
     * awaiting an Accept/Decline. They surface separately from the open
     * bidding requests so the worker doesn't miss them.
     */
    val pendingDirectRequests: List<Booking> = emptyList(),
    val completedCount: Int,
    val totalEarnings: Double,
    val monthlyEarnings: Double
)

class GetWorkerDashboardUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookingRepository: BookingRepository,
    private val workerRepository: WorkerRepository,
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(): Resource<WorkerDashboardData> {
        val user = authRepository.getCurrentUser()
            ?: return Resource.Error("Chưa đăng nhập")

        val profileResult = workerRepository.getWorkerById(user.id)
        val profile = (profileResult as? Resource.Success)?.data

        val activeResult = bookingRepository.getWorkerBookings(user.id, BookingStatus.IN_PROGRESS)
        val activeJobs = (activeResult as? Resource.Success)?.data ?: emptyList()

        val pendingCompletionResult = bookingRepository.getWorkerBookings(user.id, BookingStatus.PENDING_COMPLETION)
        val pendingCompletionJobs = (pendingCompletionResult as? Resource.Success)?.data ?: emptyList()

        val allActiveJobs = activeJobs + pendingCompletionJobs

        val confirmedResult = bookingRepository.getWorkerBookings(user.id, BookingStatus.CONFIRMED)
        val confirmedJobs = (confirmedResult as? Resource.Success)?.data ?: emptyList()

        val pendingDirectResult = bookingRepository.getPendingDirectBookings(user.id)
        val pendingDirectRequests = (pendingDirectResult as? Resource.Success)?.data ?: emptyList()

        val completedResult = bookingRepository.getWorkerBookings(user.id, BookingStatus.COMPLETED)
        val completedJobs = (completedResult as? Resource.Success)?.data ?: emptyList()

        // Cancelled bookings — both worker-initiated cancels (after a
        // confirmed/paid booking) and customer-initiated cancels land here so
        // the worker can review history. Sorted by updatedAt desc downstream.
        val cancelledResult = bookingRepository.getWorkerBookings(user.id, BookingStatus.CANCELLED)
        val cancelledJobs = (cancelledResult as? Resource.Success)?.data ?: emptyList()

        // Calculate earnings from payment history
        val paymentResult = paymentRepository.getPaymentHistory(user.id)
        val payments = (paymentResult as? Resource.Success)?.data ?: emptyList()
        val totalEarnings = payments
            .filter { it.status == com.example.fixbid.domain.model.PaymentStatus.COMPLETED }
            .sumOf { it.workerReceives }

        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val monthlyEarnings = payments
            .filter { it.status == com.example.fixbid.domain.model.PaymentStatus.COMPLETED && (it.paidAt ?: 0) >= thirtyDaysAgo }
            .sumOf { it.workerReceives }

        return Resource.Success(
            WorkerDashboardData(
                profile = profile,
                activeJobs = allActiveJobs,
                pendingJobs = confirmedJobs,
                completedJobs = completedJobs,
                cancelledJobs = cancelledJobs,
                pendingDirectRequests = pendingDirectRequests,
                completedCount = completedJobs.size + (profile?.totalJobsDone ?: 0),
                totalEarnings = totalEarnings,
                monthlyEarnings = monthlyEarnings
            )
        )
    }
}