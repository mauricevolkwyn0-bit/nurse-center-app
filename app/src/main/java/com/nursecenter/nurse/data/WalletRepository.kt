package com.nursecenter.nurse.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class WalletEntry(
    val id: String,
    val serviceName: String,
    val scheduledAt: ZonedDateTime?,
    val payout: Double,
    /** `accepted`, `in_progress` or `completed`. */
    val status: String,
) {
    val completed: Boolean get() = status == "completed"
}

data class PayoutAccount(val bankName: String, val accountNumber: String)

data class WalletData(
    /** Payouts from completed bookings. */
    val earned: Double,
    /** Payouts from accepted and in-progress bookings. */
    val pending: Double,
    val pendingShifts: Int,
    val thisMonth: Double,
    val lastMonth: Double,
    /** Most recent first. */
    val history: List<WalletEntry>,
    /** Null when no payout account is set up (or it couldn't be read). */
    val account: PayoutAccount?,
)

/** Loads the nurse's earnings from `bookings` and payout details from `caregiver_bank_accounts`, as the web wallet does. */
object WalletRepository {
    suspend fun load(): WalletData = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val zone = ZoneId.systemDefault()

        coroutineScope {
            val bookings = async {
                HomeRepository.getArray(
                    session,
                    "/rest/v1/bookings?select=id,status,caregiver_payout,scheduled_at,service:services(name)" +
                        "&caregiver_id=eq.$me&deleted_at=is.null&status=in.(accepted,in_progress,completed)" +
                        "&order=scheduled_at.desc.nullslast",
                )
            }
            // Optional, so a missing account or policy never hides the earnings.
            val bank = async {
                runCatching {
                    HomeRepository.getArray(session, "/rest/v1/caregiver_bank_accounts?select=bank_name,account_number&caregiver_id=eq.$me")
                        .optJSONObject(0)
                }.getOrNull()
            }

            val rows = bookings.await()
            val entries = (0 until rows.length()).map { i ->
                val json = rows.getJSONObject(i)
                WalletEntry(
                    id = json.getString("id"),
                    serviceName = json.optJSONObject("service")?.optStringOrNull("name") ?: "Care visit",
                    scheduledAt = json.optStringOrNull("scheduled_at")?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) },
                    payout = json.optDouble("caregiver_payout").takeUnless { it.isNaN() } ?: 0.0,
                    status = json.optString("status"),
                )
            }
            val done = entries.filter { it.completed }
            val upcoming = entries.filterNot { it.completed }
            val monthStart = ZonedDateTime.now(zone).withDayOfMonth(1).toLocalDate().atStartOfDay(zone)
            fun earnedBetween(from: ZonedDateTime, to: ZonedDateTime) =
                done.filter { e -> e.scheduledAt?.let { !it.isBefore(from) && it.isBefore(to) } == true }.sumOf { it.payout }

            WalletData(
                earned = done.sumOf { it.payout },
                pending = upcoming.sumOf { it.payout },
                pendingShifts = upcoming.size,
                thisMonth = earnedBetween(monthStart, monthStart.plusMonths(1)),
                lastMonth = earnedBetween(monthStart.minusMonths(1), monthStart),
                history = entries,
                account = bank.await()?.let { b ->
                    val bankName = b.optStringOrNull("bank_name") ?: return@let null
                    PayoutAccount(bankName, b.optString("account_number"))
                },
            )
        }
    }
}
