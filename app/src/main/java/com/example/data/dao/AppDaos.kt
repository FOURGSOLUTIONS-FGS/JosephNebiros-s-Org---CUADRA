package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ClientEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.LoanEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.RoutePointEntity
import com.example.data.model.TrackingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients WHERE isActive = 1 ORDER BY visitOrder ASC, name ASC")
    fun getAllActiveClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :clientId LIMIT 1")
    suspend fun getClientById(clientId: Long): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity): Long

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Delete
    suspend fun deleteClient(client: ClientEntity)

    @Query("SELECT COUNT(*) FROM clients WHERE isActive = 1")
    fun getActiveClientsCount(): Flow<Int>
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans WHERE status = 'ACTIVE' ORDER BY id DESC")
    fun getAllActiveLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE clientId = :clientId ORDER BY id DESC")
    fun getLoansForClient(clientId: Long): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE clientId = :clientId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveLoanForClient(clientId: Long): LoanEntity?

    @Query("SELECT * FROM loans WHERE id = :loanId LIMIT 1")
    suspend fun getLoanById(loanId: Long): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity): Long

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Query("UPDATE loans SET totalPaid = totalPaid + :amount, remainingBalance = remainingBalance - :amount, paidQuotas = paidQuotas + 1, status = CASE WHEN remainingBalance - :amount <= 0.01 THEN 'PAID' ELSE 'ACTIVE' END WHERE id = :loanId")
    suspend fun recordPaymentOnLoan(loanId: Long, amount: Double)

    @Query("SELECT SUM(amountBorrowed) FROM loans WHERE startDate >= :startOfDay AND startDate <= :endOfDay")
    fun getTotalDisbursedBetween(startOfDay: Long, endOfDay: Long): Flow<Double?>
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY date DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE date >= :startOfDay AND date <= :endOfDay ORDER BY date DESC")
    fun getPaymentsForDay(startOfDay: Long, endOfDay: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE clientId = :clientId ORDER BY date DESC")
    fun getPaymentsForClient(clientId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE loanId = :loanId ORDER BY date DESC")
    fun getPaymentsForLoan(loanId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE loanId = :loanId AND date >= :startOfDay AND date <= :endOfDay LIMIT 1")
    suspend fun getTodayPaymentForLoan(loanId: Long, startOfDay: Long, endOfDay: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE isSyncedWithCloud = 0")
    suspend fun getUnsyncedPayments(): List<PaymentEntity>

    @Query("UPDATE payments SET isSyncedWithCloud = 1 WHERE id = :paymentId")
    suspend fun markPaymentSynced(paymentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    @Query("SELECT SUM(amount) FROM payments WHERE date >= :startOfDay AND date <= :endOfDay")
    fun getTotalCollectedBetween(startOfDay: Long, endOfDay: Long): Flow<Double?>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM client_reminders ORDER BY isCompleted ASC, dueDateMillis ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM client_reminders WHERE isCompleted = 0 ORDER BY dueDateMillis ASC")
    fun getPendingReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM client_reminders WHERE clientId = :clientId ORDER BY isCompleted ASC, dueDateMillis ASC")
    fun getRemindersForClient(clientId: Long): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE client_reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun setReminderCompleted(id: Long, completed: Boolean)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("SELECT COUNT(*) FROM client_reminders WHERE isCompleted = 0")
    fun getPendingRemindersCount(): Flow<Int>
}

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: RoutePointEntity): Long

    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getPointsForSession(sessionId: String): Flow<List<RoutePointEntity>>

    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsForSessionSync(sessionId: String): List<RoutePointEntity>

    @Query("SELECT * FROM route_points ORDER BY timestamp DESC LIMIT 1")
    fun getLatestPoint(): Flow<RoutePointEntity?>

    @Query("DELETE FROM route_points WHERE sessionId = :sessionId")
    suspend fun clearSessionPoints(sessionId: String)

    // Tracking Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSession(session: TrackingSessionEntity)

    @Query("SELECT * FROM tracking_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<TrackingSessionEntity>>

    @Query("SELECT * FROM tracking_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSession(): TrackingSessionEntity?

    @Query("SELECT * FROM tracking_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): TrackingSessionEntity?
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE date >= :startOfDay AND date <= :endOfDay ORDER BY date DESC")
    fun getExpensesForDay(startOfDay: Long, endOfDay: Long): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT SUM(amount) FROM expenses WHERE date >= :startOfDay AND date <= :endOfDay")
    fun getTotalExpensesBetween(startOfDay: Long, endOfDay: Long): Flow<Double?>
}
