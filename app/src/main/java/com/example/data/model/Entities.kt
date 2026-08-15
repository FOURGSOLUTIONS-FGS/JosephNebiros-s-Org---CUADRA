package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val aliasOrBusiness: String = "",
    val phone: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String = "",
    val visitOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["clientId"])]
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clientId: Long,
    val amountBorrowed: Double,
    val interestRate: Double = 20.0, // Percentage, e.g. 20%
    val totalToPay: Double,
    val totalPaid: Double = 0.0,
    val remainingBalance: Double,
    val quotaAmount: Double,
    val totalQuotas: Int = 24, // e.g. 20, 24, 30 days
    val paidQuotas: Int = 0,
    val startDate: Long = System.currentTimeMillis(),
    val dueDate: Long = System.currentTimeMillis() + (24L * 24 * 60 * 60 * 1000),
    val frequency: String = "DIARIO", // DIARIO, LUNES_SABADO, SEMANAL
    val status: String = "ACTIVE", // ACTIVE, PAID, DEFAULTED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["loanId"]),
        Index(value = ["clientId"]),
        Index(value = ["date"])
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanId: Long,
    val clientId: Long,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val quotaNumber: Int = 1,
    val notes: String = "",
    val collectedLatitude: Double? = null,
    val collectedLongitude: Double? = null,
    val paymentMethod: String = "EFECTIVO" // EFECTIVO, TRANSFERENCIA
)

@Entity(
    tableName = "route_points",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"])
    ]
)
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "tracking_sessions")
data class TrackingSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val dateFormatted: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val pointsCount: Int = 0,
    val isActive: Boolean = true
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val concept: String,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val category: String = "RUTA" // RUTA, GASOLINA, ALIMENTACION, OTROS
)

// Combined UI data structures
data class ClientWithActiveLoan(
    val client: ClientEntity,
    val activeLoan: LoanEntity?,
    val todayPayment: PaymentEntity? = null,
    val isCollectedToday: Boolean = false
)
