package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ClientDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.LoanDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.RouteDao
import com.example.data.model.ClientEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.LoanEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.RoutePointEntity
import com.example.data.model.TrackingSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ClientEntity::class,
        LoanEntity::class,
        PaymentEntity::class,
        RoutePointEntity::class,
        TrackingSessionEntity::class,
        ExpenseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun loanDao(): LoanDao
    abstract fun paymentDao(): PaymentDao
    abstract fun routeDao(): RouteDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cobrador_diario_database"
                )
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-populate with initial starter clients & loans so the app is immediately testable and functional
                CoroutineScope(Dispatchers.IO).launch {
                    val database = getDatabase(context)
                    populateInitialData(database)
                }
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            val client1 = ClientEntity(
                name = "María González",
                aliasOrBusiness = "Tienda La Arenosa",
                phone = "+57 301 555 2345",
                address = "Cra. 43 # 54-20, Barrio Boston",
                latitude = 10.9885,
                longitude = -74.7932,
                notes = "Cobrar después de las 10:00 AM",
                visitOrder = 1
            )
            val client2 = ClientEntity(
                name = "Carlos Mendoza",
                aliasOrBusiness = "Taller Curramba Motors",
                phone = "+57 310 555 6789",
                address = "Calle 45 (Murillo) # 21-35, San José",
                latitude = 10.9612,
                longitude = -74.7865,
                notes = "Siempre paga puntual en efectivo",
                visitOrder = 2
            )
            val client3 = ClientEntity(
                name = "Rosa Elena Pérez",
                aliasOrBusiness = "Frutas y Verduras El Malecón",
                phone = "+57 315 555 9876",
                address = "Cra. 38 # 36-12, Paseo Bolívar / Centro",
                latitude = 10.9780,
                longitude = -74.7790,
                notes = "Puesto central Paseo Bolívar",
                visitOrder = 3
            )
            val client4 = ClientEntity(
                name = "Juan Diego Ramos",
                aliasOrBusiness = "Papelería Del Prado",
                phone = "+57 320 555 1122",
                address = "Cra. 54 # 68-80, Barrio El Prado",
                latitude = 10.9995,
                longitude = -74.8015,
                notes = "Abono diario a las 3:00 PM",
                visitOrder = 4
            )
            val client5 = ClientEntity(
                name = "Andrés Felipe Silva",
                aliasOrBusiness = "Droguería La Murillo",
                phone = "+57 300 555 4433",
                address = "Calle 45 # 14-08, La Victoria",
                latitude = 10.9525,
                longitude = -74.7810,
                notes = "Cobrar antes de las 5:00 PM",
                visitOrder = 5
            )
            val client6 = ClientEntity(
                name = "Luz Marina Castro",
                aliasOrBusiness = "Panadería La 84",
                phone = "+57 312 555 8899",
                address = "Calle 84 # 47-15, Alto Prado",
                latitude = 11.0080,
                longitude = -74.8180,
                notes = "Paga con efectivo o Nequi",
                visitOrder = 6
            )

            val id1 = db.clientDao().insertClient(client1)
            val id2 = db.clientDao().insertClient(client2)
            val id3 = db.clientDao().insertClient(client3)
            val id4 = db.clientDao().insertClient(client4)
            val id5 = db.clientDao().insertClient(client5)
            val id6 = db.clientDao().insertClient(client6)

            // Create initial loans in Colombian Pesos (COP)
            // Loan 1: $1.000.000 + 20% = $1.200.000 (24 cuotas de $50.000)
            val loan1 = LoanEntity(
                clientId = id1,
                amountBorrowed = 1000000.0,
                interestRate = 20.0,
                totalToPay = 1200000.0,
                totalPaid = 350000.0,
                remainingBalance = 850000.0,
                quotaAmount = 50000.0,
                totalQuotas = 24,
                paidQuotas = 7,
                startDate = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000),
                dueDate = System.currentTimeMillis() + (17L * 24 * 60 * 60 * 1000),
                frequency = "DIARIO",
                status = "ACTIVE"
            )
            // Loan 2: $2.000.000 + 20% = $2.400.000 (24 cuotas de $100.000)
            val loan2 = LoanEntity(
                clientId = id2,
                amountBorrowed = 2000000.0,
                interestRate = 20.0,
                totalToPay = 2400000.0,
                totalPaid = 1200000.0,
                remainingBalance = 1200000.0,
                quotaAmount = 100000.0,
                totalQuotas = 24,
                paidQuotas = 12,
                startDate = System.currentTimeMillis() - (12L * 24 * 60 * 60 * 1000),
                dueDate = System.currentTimeMillis() + (12L * 24 * 60 * 60 * 1000),
                frequency = "DIARIO",
                status = "ACTIVE"
            )
            // Loan 3: $600.000 + 20% = $720.000 (24 cuotas de $30.000)
            val loan3 = LoanEntity(
                clientId = id3,
                amountBorrowed = 600000.0,
                interestRate = 20.0,
                totalToPay = 720000.0,
                totalPaid = 90000.0,
                remainingBalance = 630000.0,
                quotaAmount = 30000.0,
                totalQuotas = 24,
                paidQuotas = 3,
                startDate = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000),
                dueDate = System.currentTimeMillis() + (21L * 24 * 60 * 60 * 1000),
                frequency = "DIARIO",
                status = "ACTIVE"
            )
            // Loan 4: $1.500.000 + 20% = $1.800.000 (24 cuotas de $75.000)
            val loan4 = LoanEntity(
                clientId = id4,
                amountBorrowed = 1500000.0,
                interestRate = 20.0,
                totalToPay = 1800000.0,
                totalPaid = 0.0,
                remainingBalance = 1800000.0,
                quotaAmount = 75000.0,
                totalQuotas = 24,
                paidQuotas = 0,
                startDate = System.currentTimeMillis(),
                dueDate = System.currentTimeMillis() + (24L * 24 * 60 * 60 * 1000),
                frequency = "DIARIO",
                status = "ACTIVE"
            )
            // Loan 5: $800.000 + 20% = $960.000 (24 cuotas de $40.000)
            val loan5 = LoanEntity(
                clientId = id5,
                amountBorrowed = 800000.0,
                interestRate = 20.0,
                totalToPay = 960000.0,
                totalPaid = 200000.0,
                remainingBalance = 760000.0,
                quotaAmount = 40000.0,
                totalQuotas = 24,
                paidQuotas = 5,
                startDate = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000),
                dueDate = System.currentTimeMillis() + (19L * 24 * 60 * 60 * 1000),
                frequency = "DIARIO",
                status = "ACTIVE"
            )
            // Loan 6: $1.200.000 + 20% = $1.440.000 (24 cuotas de $60.000)
            val loan6 = LoanEntity(
                clientId = id6,
                amountBorrowed = 1200000.0,
                interestRate = 20.0,
                totalToPay = 1440000.0,
                totalPaid = 360000.0,
                remainingBalance = 1080000.0,
                quotaAmount = 60000.0,
                totalQuotas = 24,
                paidQuotas = 6,
                startDate = System.currentTimeMillis() - (6L * 24 * 60 * 60 * 1000),
                dueDate = System.currentTimeMillis() + (18L * 24 * 60 * 60 * 1000),
                frequency = "DIARIO",
                status = "ACTIVE"
            )

            db.loanDao().insertLoan(loan1)
            db.loanDao().insertLoan(loan2)
            db.loanDao().insertLoan(loan3)
            db.loanDao().insertLoan(loan4)
            db.loanDao().insertLoan(loan5)
            db.loanDao().insertLoan(loan6)

            // Seed initial Barranquilla tracking route session
            val sessionName = "RUTA_BARRANQUILLA_CENTRO_NORTE"
            val todayDateFormatted = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            db.routeDao().insertOrUpdateSession(
                TrackingSessionEntity(
                    sessionId = sessionName,
                    dateFormatted = todayDateFormatted,
                    startTime = System.currentTimeMillis() - (2 * 60 * 60 * 1000),
                    endTime = System.currentTimeMillis(),
                    totalDistanceMeters = 8420.0,
                    isActive = false
                )
            )

            val routeCoords = listOf(
                Pair(10.9780, -74.7790), // Paseo Bolívar
                Pair(10.9820, -74.7850), // Vía 40 / Barrio Abajo
                Pair(10.9885, -74.7932), // Boston
                Pair(10.9940, -74.7980), // Carrera 46
                Pair(10.9995, -74.8015), // El Prado
                Pair(11.0040, -74.8100), // Parque Washington
                Pair(11.0080, -74.8180)  // Alto Prado
            )

            routeCoords.forEachIndexed { idx, (lat, lng) ->
                db.routeDao().insertPoint(
                    RoutePointEntity(
                        sessionId = sessionName,
                        latitude = lat,
                        longitude = lng,
                        accuracy = 4.2f,
                        speed = 6.5f,
                        timestamp = System.currentTimeMillis() - ((routeCoords.size - idx) * 12 * 60 * 1000L)
                    )
                )
            }
        }
    }
}
