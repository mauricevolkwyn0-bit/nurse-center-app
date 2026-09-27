package com.nursecenter.nurse.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.nursecenter.nurse.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class UploadedDocument(
    val id: String,
    val path: String,
    val verified: Boolean,
    val uploadedAt: ZonedDateTime,
)

/** One document the nurse is asked for, and their upload of it if any. */
data class DocumentSlot(
    /** `caregiver_documents.type`. */
    val type: String,
    val label: String,
    val description: String?,
    val required: Boolean,
    val uploaded: UploadedDocument?,
)

/**
 * The nurse's verification documents in `caregiver_documents`, with files in the private `certificates`
 * storage bucket under `<user id>/`. Mirrors the web nurse Documents page, including which documents
 * each profession must provide.
 */
object DocumentsRepository {
    private const val BUCKET = "certificates"
    /** The bucket's size limit. */
    const val MAX_BYTES = 10L * 1024 * 1024

    private class Doc(val type: String, val label: String, val required: Boolean, val description: String? = null)

    private val UNIVERSAL = listOf(
        Doc("national_id", "National ID / Passport", true, "SA ID, Passport or ID card"),
        Doc("police_clearance", "Police Clearance", false, "Can be submitted later — typically takes 6–8 weeks"),
    )

    private val BY_PROFESSION = mapOf(
        "Registered Nurses" to listOf(
            Doc("qualification_certificate", "B.Sc Nursing / Diploma", true),
            Doc("sanc_certificate", "SANC Registration Certificate", true, "Professional Nurse registration"),
            Doc("cpr_certificate", "CPR / BLS Certificate", true),
            Doc("other", "Additional Certificate", false, "Critical Care PGD, Phlebotomy, etc."),
        ),
        "Home & Community Nurses" to listOf(
            Doc("qualification_certificate", "Diploma / Higher Certificate", true),
            Doc("sanc_certificate", "SANC Registration Certificate", true, "General / Enrolled Nurse"),
            Doc("cpr_certificate", "CPR / First Aid Certificate", true),
        ),
        "Midwives & Maternal Nurses" to listOf(
            Doc("qualification_certificate", "Midwifery Degree / Diploma", true),
            Doc("sanc_certificate", "SANC Registration Certificate", true, "Registered Midwife"),
            Doc("cpr_certificate", "Neonatal Resuscitation / CPR Certificate", true),
        ),
        "Caregivers & Home Care Aides" to listOf(
            Doc("qualification_certificate", "NQF Caregiver Certificate", true, "SETA accredited qualification"),
            Doc("cpr_certificate", "CPR / First Aid Certificate", true, "Level 1–2"),
        ),
        "Physiotherapists" to listOf(
            Doc("qualification_certificate", "B.Sc Physiotherapy Degree", true),
            Doc("sanc_certificate", "HPCSA Registration Certificate", true),
            Doc("cpr_certificate", "CPR / BLS Certificate", true),
        ),
    )

    /** Used when the nurse has no specialty yet. */
    private val DEFAULT = listOf(
        Doc("qualification_certificate", "Qualification Certificate", true),
        Doc("sanc_certificate", "SANC / HPCSA Certificate", false),
        Doc("cpr_certificate", "CPR Certificate", false),
        Doc("other", "Supporting Document", false),
    )

    private val SPECIALTY_TO_PROFESSION = mapOf(
        "Post-Surgery Care" to "Registered Nurses", "Post-Surgery" to "Registered Nurses",
        "Injections & IV Therapy" to "Registered Nurses", "Wound Care" to "Registered Nurses",
        "ICU / Critical" to "Registered Nurses", "Phlebotomist" to "Registered Nurses",
        "Home Nursing Visits" to "Home & Community Nurses", "Chronic Illness Support" to "Home & Community Nurses",
        "Chronic Illness" to "Home & Community Nurses", "Preventive Health" to "Home & Community Nurses",
        "Mother & Baby Care" to "Midwives & Maternal Nurses", "Mother & Baby" to "Midwives & Maternal Nurses",
        "Elderly Care" to "Caregivers & Home Care Aides", "Overnight Care" to "Caregivers & Home Care Aides",
        "Home Care" to "Caregivers & Home Care Aides", "Physiotherapy" to "Physiotherapists",
    )

    suspend fun load(): List<DocumentSlot> = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        coroutineScope {
            val specialty = async {
                runCatching {
                    HomeRepository.getArray(session, "/rest/v1/caregiver_profiles?select=specialty&id=eq.$me")
                        .optJSONObject(0)?.optStringOrNull("specialty")
                }.getOrNull()
            }
            val rows = async {
                HomeRepository.getArray(
                    session, "/rest/v1/caregiver_documents?select=id,type,file_url,verified,uploaded_at&caregiver_id=eq.$me&order=uploaded_at.asc",
                )
            }
            val zone = ZoneId.systemDefault()
            val array = rows.await()
            // Latest upload per type wins.
            val uploads = (0 until array.length()).map { array.getJSONObject(it) }.associate { json ->
                json.getString("type") to UploadedDocument(
                    id = json.getString("id"),
                    path = json.optString("file_url"),
                    verified = json.optBoolean("verified"),
                    uploadedAt = OffsetDateTime.parse(json.getString("uploaded_at")).atZoneSameInstant(zone),
                )
            }
            val profession = specialty.await()?.let { SPECIALTY_TO_PROFESSION[it] }
            val docs = UNIVERSAL + (profession?.let { BY_PROFESSION[it] } ?: DEFAULT)
            docs.map { DocumentSlot(it.type, it.label, it.description, it.required, uploads[it.type]) }
        }
    }

    /** Uploads [uri] as the nurse's [type] document, replacing any earlier upload of that type. */
    suspend fun upload(context: Context, type: String, uri: Uri) = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val me = session.userId
        val resolver = context.contentResolver

        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
        if (size != null && size > MAX_BYTES) throw AuthException("That file is too large. Please choose one under 10 MB.")
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (!mime.startsWith("image/") && mime != "application/pdf") throw AuthException("Please choose a PDF or a photo.")
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw AuthException("Couldn't read that file. Please try another.")
        if (bytes.size > MAX_BYTES) throw AuthException("That file is too large. Please choose one under 10 MB.")

        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (mime == "application/pdf") "pdf" else "jpg"
        val path = "$me/${type}_${System.currentTimeMillis()}.$ext"
        val (code, _) = try {
            SupabaseAuth.upload("/storage/v1/object/$BUCKET/$path", bytes, mime, session.accessToken, mapOf("x-upsert" to "true"))
        } catch (e: IOException) {
            throw AuthException("Upload failed. Check your internet connection and try again.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) throw AuthException("Couldn't upload your document ($code). Please try again.")

        // Record the new file first, then drop older rows of this type, so a failure never leaves the nurse with none.
        val previous = HomeRepository.getArray(session, "/rest/v1/caregiver_documents?select=id&caregiver_id=eq.$me&type=eq.$type")
        val body = JSONObject().put("caregiver_id", me).put("type", type).put("file_url", path).put("verified", false)
        rest(session, "POST", "/rest/v1/caregiver_documents", body.toString())
        val oldIds = (0 until previous.length()).map { previous.getJSONObject(it).getString("id") }
        if (oldIds.isNotEmpty()) rest(session, "DELETE", "/rest/v1/caregiver_documents?id=in.(${oldIds.joinToString(",")})", null)
    }

    /** A short-lived link to view an uploaded document (the bucket is private). */
    suspend fun viewUrl(document: UploadedDocument): String = withContext(Dispatchers.IO) {
        val session = SupabaseAuth.validSession()
        val encoded = document.path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val (code, response) = try {
            SupabaseAuth.request("POST", "/storage/v1/object/sign/$BUCKET/$encoded", JSONObject().put("expiresIn", 600).toString(), session.accessToken)
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code !in 200..299) throw AuthException("Couldn't open this document ($code).")
        val signed = JSONObject(response).optString("signedURL").ifBlank { throw AuthException("Couldn't open this document.") }
        BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1" + signed
    }

    private fun rest(session: AuthSession, method: String, path: String, body: String?): JSONArray {
        val (code, response) = try {
            SupabaseAuth.request(method, path, body, session.accessToken, mapOf("Prefer" to "return=representation"))
        } catch (e: IOException) {
            throw AuthException("Can't reach the server. Check your internet connection.")
        }
        if (code == 401) throw AuthException("Your session has expired. Please sign in again.")
        if (code !in 200..299) throw AuthException("Couldn't save your document ($code). Please try again.")
        return JSONArray(response.ifBlank { "[]" })
    }
}
