package com.nursecenter.nurse.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nursecenter.nurse.BuildConfig
import com.nursecenter.nurse.data.AccountSummary
import com.nursecenter.nurse.data.AuthException
import com.nursecenter.nurse.data.DocumentSlot
import com.nursecenter.nurse.data.DocumentsRepository
import com.nursecenter.nurse.data.MoreRepository
import com.nursecenter.nurse.data.NurseProfile
import com.nursecenter.nurse.data.ProfileRepository
import com.nursecenter.nurse.data.RequestRealtime
import com.nursecenter.nurse.data.Review
import com.nursecenter.nurse.data.ReviewsData
import com.nursecenter.nurse.data.ReviewsRepository
import com.nursecenter.nurse.data.SUPPORT_PHONE
import com.nursecenter.nurse.data.SupabaseAuth
import com.nursecenter.nurse.data.UploadedDocument
import com.nursecenter.nurse.data.WalletData
import com.nursecenter.nurse.data.WalletEntry
import com.nursecenter.nurse.data.WalletRepository
import com.nursecenter.nurse.ui.CardList
import com.nursecenter.nurse.ui.Eyebrow
import com.nursecenter.nurse.ui.IconBtn
import com.nursecenter.nurse.ui.IconTile
import com.nursecenter.nurse.ui.NcSheet
import com.nursecenter.nurse.ui.PageTitle
import com.nursecenter.nurse.ui.RemoteImage
import com.nursecenter.nurse.ui.SecondaryButton
import com.nursecenter.nurse.ui.SectionTitle
import com.nursecenter.nurse.ui.SoftCard
import com.nursecenter.nurse.ui.Txt
import com.nursecenter.nurse.ui.countUp
import com.nursecenter.nurse.ui.enterUp
import com.nursecenter.nurse.ui.pressable
import com.nursecenter.nurse.ui.rand
import com.nursecenter.nurse.ui.rememberComingSoon
import com.nursecenter.nurse.ui.theme.Jakarta
import com.nursecenter.nurse.ui.theme.NC
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MoreScreen(goTo: (Screen) -> Unit, onSignOut: () -> Unit) {
    val soon = rememberComingSoon()
    var account by remember { mutableStateOf<AccountSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val changes by RequestRealtime.changes.collectAsState()

    LaunchedEffect(reload, changes) {
        error = null
        try {
            account = MoreRepository.load()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your account. Please try again."
        }
    }

    // While loading, rows show "Loading…"; a line whose data couldn't be read falls back to a generic description.
    val loading = account == null && error == null
    fun line(value: AccountSummary.() -> String?, fallback: String) =
        if (loading) "Loading…" else account?.value() ?: fallback

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Column(Modifier.enterUp(0)) {
            Eyebrow("Your account")
            PageTitle("More", Modifier.padding(top = 4.dp))
        }
        val name = account?.fullName ?: SupabaseAuth.session?.email.orEmpty()
        Row(
            Modifier.enterUp(1).padding(top = 20.dp).fillMaxWidth().background(NC.DeepTeal, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF45C4B3)), contentAlignment = Alignment.Center) {
                Txt(initialsOf(name), 18, Color(0xFF14383D), weight = FontWeight.Bold)
                RemoteImage(account?.avatarUrl, "Profile photo")
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Txt(name.ifEmpty { "Loading…" }, 17, Color.White, weight = FontWeight.Bold, maxLines = 1)
                Txt(account?.title ?: "Nurse", 12, Color.White.copy(alpha = 0.55f), Modifier.padding(top = 4.dp), maxLines = 1)
            }
            account?.verificationStatus?.let { VerificationBadge(it) }
        }
        error?.let { ErrorNote(it, Modifier.padding(top = 12.dp)) { reload++ } }
        CardList(
            listOf(
                { MenuRow(Icons.Outlined.AccountCircle, "Profile & personal details", "Contact and professional info") { goTo(Screen.Profile) } },
                { MenuRow(Icons.Outlined.TaskAlt, "Documents", line({ documentsLine() }, "Credentials & compliance")) { goTo(Screen.Documents) } },
                { MenuRow(Icons.Outlined.AccountBalanceWallet, "Wallet & payouts", line({ walletLine() }, "Earnings & payouts")) { goTo(Screen.Wallet) } },
                { MenuRow(Icons.Outlined.StarOutline, "Reviews", line({ reviewsLine() }, "What clients say")) { goTo(Screen.Reviews) } },
            ),
            Modifier.enterUp(2).padding(top = 20.dp),
        )
        CardList(
            listOf(
                { MenuRow(Icons.Outlined.HeadsetMic, "Help & support") { goTo(Screen.Support) } },
                { MenuRow(Icons.Rounded.PowerSettingsNew, "Sign out", danger = true, onClick = onSignOut) },
            ),
            Modifier.enterUp(3).padding(top = 16.dp),
        )
        Txt(
            "Nurse Center Nurse Portal · v${BuildConfig.VERSION_NAME}", 11, NC.Faint,
            Modifier.fillMaxWidth().padding(vertical = 24.dp), weight = FontWeight.Medium, align = TextAlign.Center,
        )
    }
}

private fun AccountSummary.documentsLine(): String? {
    val uploaded = documentsUploaded ?: return null
    val verified = documentsVerified ?: 0
    return when {
        uploaded == 0 -> "No documents uploaded yet"
        verified == uploaded -> "$verified verified ${if (verified == 1) "document" else "documents"}"
        else -> "$verified of $uploaded documents verified"
    }
}

private fun AccountSummary.walletLine(): String? {
    val earned = earned ?: return null
    val pending = pending ?: 0.0
    return if (pending > 0) "Earned ${rand(earned.toFloat(), cents = true)} · ${rand(pending.toFloat(), cents = true)} pending"
    else "Earned ${rand(earned.toFloat(), cents = true)}"
}

private fun AccountSummary.reviewsLine(): String? {
    val count = reviewCount ?: return null
    if (count == 0) return "No reviews yet"
    val average = String.format(Locale.US, "%.1f", rating ?: 0.0)
    return "$average average from $count ${if (count == 1) "review" else "reviews"}"
}

@Composable
private fun VerificationBadge(status: String) {
    val (label, color) = when (status) {
        "verified" -> "Verified" to Color(0xFF8DE0C4)
        "pending" -> "In review" to Color(0xFFF5CF83)
        "rejected" -> "Action needed" to Color(0xFFF4A49E)
        else -> "Not verified" to Color.White.copy(alpha = 0.6f)
    }
    Row(
        Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == "verified") {
            Icon(Icons.Outlined.VerifiedUser, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Txt(label, 11, color, weight = FontWeight.Bold)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(RoundedCornerShape(0.dp), pressedScale = 0.985f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon,
            background = if (danger) Color(0xFFFFF0EF) else NC.TealSoft,
            tint = if (danger) Color(0xFFDD6D65) else Color(0xFF188F8C),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Txt(title, 14, if (danger) NC.Danger else Color(0xFF2C3F4A), weight = FontWeight.Bold)
            if (subtitle != null) Txt(subtitle, 12, NC.Muted, Modifier.padding(top = 4.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFFABB5B8))
    }
}

@Composable
fun SubScreen(
    title: String,
    subtitle: String,
    back: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", back)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Txt(title, 19, Color(0xFF253844), weight = FontWeight.Bold)
                Txt(subtitle, 12, NC.Muted, Modifier.padding(top = 2.dp))
            }
            action?.invoke()
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = content)
    }
}


@Composable
fun WalletScreen(back: () -> Unit) {
    var wallet by remember { mutableStateOf<WalletData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val changes by RequestRealtime.changes.collectAsState()

    LaunchedEffect(reload, changes) {
        error = null
        try {
            wallet = WalletRepository.load()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your wallet. Please try again."
        }
    }

    SubScreen("My wallet", "Earnings & payouts", back) {
        val w = wallet
        Column(
            Modifier.enterUp(0).padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(NC.DeepTeal)
        ) {
            Box {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 50.dp, y = (-60).dp).size(170.dp)
                        .background(NC.Mint.copy(alpha = 0.10f), CircleShape)
                )
                Column(Modifier.padding(20.dp)) {
                    Txt("Total earned", 12, Color.White.copy(alpha = 0.55f), weight = FontWeight.SemiBold)
                    Txt(
                        rand(countUp((w?.earned ?: 0.0).toFloat(), 1100), cents = true), 32, Color.White,
                        Modifier.padding(top = 8.dp), weight = FontWeight.Bold, spacing = -0.03f,
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    Column(Modifier.padding(top = 16.dp)) {
                        Txt("Payout account", 11, Color.White.copy(alpha = 0.5f))
                        Txt(
                            when {
                                w == null -> "Loading…"
                                w.account == null -> "Not set up yet · add one on the website"
                                else -> "${w.account.bankName} ····${w.account.accountNumber.takeLast(4)}"
                            },
                            13, Color.White, Modifier.padding(top = 4.dp), weight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        error?.let { ErrorNote(it, Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) { reload++ } }
        Row(Modifier.enterUp(1).padding(top = 24.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftCard(Modifier.weight(1f)) {
                Txt("This month", 12, Color(0xFF839197))
                Txt(rand(countUp((w?.thisMonth ?: 0.0).toFloat())), 19, Color(0xFF263944), Modifier.padding(top = 8.dp), weight = FontWeight.Bold)
                val (trend, trendColor) = monthTrend(w)
                Txt(trend, 11, trendColor, Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
            }
            SoftCard(Modifier.weight(1f)) {
                Txt("Pending", 12, Color(0xFF839197))
                Txt(rand(countUp((w?.pending ?: 0.0).toFloat())), 19, Color(0xFF263944), Modifier.padding(top = 8.dp), weight = FontWeight.Bold)
                val shifts = w?.pendingShifts ?: 0
                Txt(
                    if (w == null) " " else if (shifts == 1) "1 upcoming shift" else "$shifts upcoming shifts",
                    11, Color(0xFF94A0A5), Modifier.padding(top = 4.dp),
                )
            }
        }
        Column(Modifier.enterUp(2).padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 28.dp)) {
            SectionTitle("Earnings history")
            when {
                w == null && error == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
                w == null -> Unit
                w.history.isEmpty() -> SoftCard(Modifier.fillMaxWidth()) {
                    Txt("No earnings yet", 14, Color(0xFF293C47), weight = FontWeight.Bold)
                    Txt("Completed jobs will appear here.", 12, Color(0xFF819096), Modifier.padding(top = 4.dp))
                }
                else -> CardList(w.history.take(50).map { entry -> { EarningRow(entry) } })
            }
            Txt(
                "Payouts are processed within 2 business days of job completion.", 11, Color(0xFF909CA1),
                Modifier.padding(top = 16.dp).widthIn(max = 280.dp).align(Alignment.CenterHorizontally),
                lineHeight = 16, align = TextAlign.Center,
            )
        }
    }
}

/** "+18% from August", "No earnings in August" etc., comparing this month's earnings with last month's. */
private fun monthTrend(w: WalletData?): Pair<String, Color> {
    if (w == null) return " " to NC.Muted
    val lastMonth = LocalDate.now().minusMonths(1).month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
    if (w.lastMonth <= 0.0) return "No earnings in $lastMonth" to Color(0xFF94A0A5)
    val change = ((w.thisMonth - w.lastMonth) / w.lastMonth * 100).roundToInt()
    return when {
        change > 0 -> "+$change% from $lastMonth" to NC.Success
        change < 0 -> "$change% from $lastMonth" to Color(0xFFC67B72)
        else -> "Same as $lastMonth" to Color(0xFF94A0A5)
    }
}

@Composable
private fun EarningRow(entry: WalletEntry) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(
            Icons.Outlined.AccountBalanceWallet,
            background = if (entry.completed) Color(0xFFE8F7F4) else Color(0xFFFFF6E8),
            tint = if (entry.completed) Color(0xFF1C9792) else Color(0xFFD29A3A),
            size = 36.dp, iconSize = 17.dp,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Txt(entry.serviceName, 13, Color(0xFF31434D), weight = FontWeight.Bold, maxLines = 1)
            val date = entry.scheduledAt?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) ?: "No date"
            Txt(
                if (entry.completed) date else "$date · ${if (entry.status == "in_progress") "In progress" else "Upcoming"}",
                11, Color(0xFF929EA3), Modifier.padding(top = 4.dp),
            )
        }
        Txt(
            (if (entry.completed) "+ " else "") + rand(entry.payout.toFloat()), 13,
            if (entry.completed) Color(0xFF239C76) else Color(0xFF94A0A5), weight = FontWeight.Bold,
        )
    }
}

@Composable
fun ProfileScreen(back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<NurseProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // Drafts while editing; the saved values stay in [profile] until a save succeeds.
    var name by rememberSaveable { mutableStateOf("") }
    var experience by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(reload) {
        error = null
        try {
            profile = ProfileRepository.load()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your profile. Please try again."
        }
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun save() {
        val trimmedName = name.trim()
        val years = experience.trim().toIntOrNull()
        when {
            trimmedName.isEmpty() -> toast("Please enter your full name.")
            experience.isNotBlank() && (years == null || years !in 0..70) -> toast("Years of experience must be a number from 0 to 70.")
            else -> {
                saving = true
                val trimmedBio = bio.trim().ifEmpty { null }
                scope.launch {
                    try {
                        ProfileRepository.save(trimmedName, years, trimmedBio)
                        profile = profile?.copy(fullName = trimmedName, yearsExperience = years, bio = trimmedBio)
                        editing = false
                        toast("Profile saved")
                    } catch (e: Exception) {
                        toast((e as? AuthException)?.message ?: "Couldn't save your profile. Please try again.")
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }

    val p = profile
    SubScreen(
        "Profile", "Personal & professional details", back,
        action = if (p == null) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editing && !saving) {
                        Txt("Cancel", 13, NC.Muted, Modifier.pressable(RoundedCornerShape(8.dp)) { editing = false }.padding(8.dp), weight = FontWeight.Bold)
                    }
                    if (saving) {
                        CircularProgressIndicator(color = NC.Teal, strokeWidth = 2.dp, modifier = Modifier.padding(8.dp).size(18.dp))
                    } else {
                        Txt(
                            if (editing) "Save" else "Edit", 13, Color(0xFF168E8C),
                            Modifier.pressable(RoundedCornerShape(8.dp)) {
                                if (editing) {
                                    save()
                                } else {
                                    name = p.fullName
                                    experience = p.yearsExperience?.toString().orEmpty()
                                    bio = p.bio.orEmpty()
                                    editing = true
                                }
                            }.padding(8.dp),
                            weight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    ) {
        when {
            p == null && error != null -> Box(Modifier.padding(20.dp)) { ErrorNote(error!!) { reload++ } }
            p == null -> Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
            else -> {
                val shownName = if (editing) name else p.fullName
                Column(
                    Modifier.enterUp(0).padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(22.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(Color(0xFFDFF4F0)), contentAlignment = Alignment.Center) {
                        Txt(initialsOf(shownName), 22, Color(0xFF158B88), weight = FontWeight.Bold)
                        RemoteImage(p.avatarUrl, "Profile photo")
                    }
                    Txt(shownName.ifBlank { p.email }, 18, Color(0xFF293C47), Modifier.padding(top = 12.dp), weight = FontWeight.Bold, align = TextAlign.Center)
                    val subtitle = p.sancNumber?.let { "SANC No. $it" } ?: p.qualification
                    subtitle?.let { Txt(it, 12, Color(0xFF859399), Modifier.padding(top = 4.dp)) }
                }
                Column(
                    Modifier.enterUp(1).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileField("Full name", shownName, editing) { name = it }
                    ProfileField("Email address", p.email, editing, editable = false)
                    ProfileField("Mobile number", p.phone ?: "Not added", editing, editable = false)
                    ProfileField("Profession", p.specialty ?: p.qualification ?: "Not set", editing, editable = false)
                    ProfileField(
                        "Years of experience", if (editing) experience else p.yearsExperience?.toString() ?: "Not set", editing,
                        keyboardType = KeyboardType.Number,
                    ) { experience = it.filter(Char::isDigit).take(2) }
                    ProfileField("About you", if (editing) bio else p.bio ?: "Not set", editing, singleLine = false) { bio = it }
                    ProfileField("Service area", p.serviceArea ?: "Not set", editing, editable = false)
                    if (editing) {
                        Txt(
                            "Your email, mobile number, profession and service area can be changed on the Nurse Center website.",
                            11, Color(0xFF909CA1), lineHeight = 16,
                        )
                    }
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** An error message with a "Try again" link. */
@Composable
private fun ErrorNote(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(Color(0xFFFFF4F3), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(message, 12, Color(0xFFB25B54), Modifier.weight(1f), lineHeight = 17)
        Txt(
            "Try again", 12, NC.TealText,
            Modifier.padding(start = 8.dp).pressable(RoundedCornerShape(8.dp), onClick = onRetry).padding(4.dp),
            weight = FontWeight.Bold,
        )
    }
}

/** @param editable false for fields that can only be changed on the website; they stay greyed out while editing. */
@Composable
private fun ProfileField(
    label: String,
    value: String,
    editing: Boolean,
    editable: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit = {},
) {
    val active = editing && editable
    val border by animateColorAsState(if (active) Color(0xFF62C5BD) else Color(0xFFE1E8E8), label = "profileBorder")
    val bg by animateColorAsState(if (active) Color.White else Color(0xFFF4F7F6), label = "profileBg")
    val shape = RoundedCornerShape(14.dp)
    Column {
        Txt(label, 12, Color(0xFF77888F), Modifier.padding(bottom = 8.dp), weight = FontWeight.Bold)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            readOnly = !active,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = KeyboardCapitalization.Sentences),
            textStyle = TextStyle(
                fontFamily = Jakarta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp,
                color = if (active) Color(0xFF243642) else if (editing) Color(0xFF9AA7AC) else Color(0xFF4B5E67),
            ),
            cursorBrush = SolidColor(NC.Teal),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = if (singleLine) 48.dp else 96.dp).background(bg, shape).border(1.dp, border, shape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) { inner() }
            },
        )
    }
}

@Composable
fun DocumentsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slots by remember { mutableStateOf<List<DocumentSlot>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    // The document type being uploaded: set when the nurse picks it, kept while the file picker is open.
    var pendingType by rememberSaveable { mutableStateOf<String?>(null) }
    var uploadingType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        error = null
        try {
            slots = DocumentsRepository.load()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your documents. Please try again."
        }
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // Android's file picker: the nurse chooses a PDF or photo from their phone; no storage permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val type = pendingType
        pendingType = null
        if (uri == null || type == null) return@rememberLauncherForActivityResult
        uploadingType = type
        scope.launch {
            try {
                DocumentsRepository.upload(context, type, uri)
                toast("Document uploaded. We'll review it shortly.")
                reload++
            } catch (e: Exception) {
                toast((e as? AuthException)?.message ?: "Couldn't upload your document. Please try again.")
            } finally {
                uploadingType = null
            }
        }
    }
    fun pickFile(type: String) {
        pendingType = type
        runCatching { picker.launch(arrayOf("application/pdf", "image/*")) }
            .onFailure { pendingType = null; toast("No file browser found on this phone.") }
    }

    fun view(document: UploadedDocument) {
        scope.launch {
            try {
                val url = DocumentsRepository.viewUrl(document)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                toast((e as? AuthException)?.message ?: "Couldn't open this document.")
            }
        }
    }

    SubScreen("Documents", "Credentials & compliance", back) {
        val s = slots
        when {
            s == null && error != null -> ErrorNote(error!!, Modifier.padding(20.dp)) { reload++ }
            s == null -> Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
            else -> {
                DocumentsStatus(s, Modifier.enterUp(0).padding(horizontal = 20.dp).padding(top = 12.dp))
                Column(Modifier.enterUp(1).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 28.dp)) {
                    SectionTitle("Your documents")
                    CardList(
                        s.map { slot ->
                            {
                                DocumentRow(slot, uploading = uploadingType == slot.type) {
                                    val uploaded = slot.uploaded
                                    when {
                                        uploadingType != null -> Unit
                                        uploaded != null -> view(uploaded)
                                        else -> pickFile(slot.type)
                                    }
                                }
                            }
                        }
                    )
                    SecondaryButton(
                        "Upload document", Icons.Outlined.CloudUpload,
                        { if (uploadingType == null) choosing = true else toast("Please wait for the current upload to finish.") },
                        Modifier.padding(top = 20.dp).fillMaxWidth(),
                    )
                    Txt(
                        "PDF, JPG or PNG up to 10 MB. Your documents are private and only used for verification.", 11, Color(0xFF909CA1),
                        Modifier.padding(top = 16.dp).widthIn(max = 280.dp).align(Alignment.CenterHorizontally),
                        lineHeight = 16, align = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (choosing) {
        DocumentTypeSheet(
            slots.orEmpty(),
            onDismiss = { choosing = false },
            onChoose = { type -> choosing = false; pickFile(type) },
        )
    }
}

/** Overall verification status, like the banner on the web Documents page. */
@Composable
private fun DocumentsStatus(slots: List<DocumentSlot>, modifier: Modifier = Modifier) {
    val missing = slots.count { it.required && it.uploaded == null }
    val inReview = slots.count { it.uploaded != null && !it.uploaded.verified }
    val (title, detail) = when {
        missing > 0 -> "$missing required ${if (missing == 1) "document" else "documents"} missing" to
            "Upload them so you can accept bookings."
        inReview > 0 -> "Documents under review" to "The Nurse Center team is checking your uploads."
        else -> "Profile fully verified" to "All your documents have been verified."
    }
    val (bg, fg, sub, icon) = when {
        missing > 0 -> listOf(Color(0xFFFFF4E6), Color(0xFF8A5A14), Color(0xFFA27B45), Color(0xFFD29A3A))
        inReview > 0 -> listOf(Color(0xFFEEF5F4), Color(0xFF2C5552), Color(0xFF69807F), Color(0xFF1C9891))
        else -> listOf(Color(0xFFE8F7F2), Color(0xFF28534A), Color(0xFF69827C), Color(0xFF209C7B))
    }
    Row(modifier.fillMaxWidth().background(bg, RoundedCornerShape(18.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (missing > 0) Icons.Outlined.Description else Icons.Outlined.VerifiedUser, null, tint = icon, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Txt(title, 13, fg, weight = FontWeight.Bold)
            Txt(detail, 11, sub, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DocumentRow(slot: DocumentSlot, uploading: Boolean, onClick: () -> Unit) {
    val uploaded = slot.uploaded
    Row(
        Modifier.fillMaxWidth().pressable(RoundedCornerShape(0.dp), pressedScale = 0.985f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            if (uploaded != null) Icons.Outlined.Description else Icons.Outlined.CloudUpload,
            if (uploaded != null) NC.TealSoft else Color(0xFFF3F5F5),
            if (uploaded != null) Color(0xFF178E89) else Color(0xFF8B989E),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Txt(slot.label, 13, Color(0xFF30434D), weight = FontWeight.Bold)
            val date = uploaded?.uploadedAt?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            Txt(
                when {
                    uploading -> "Uploading…"
                    date != null -> "Uploaded $date · tap to view"
                    else -> "Not uploaded · tap to upload"
                },
                11, Color(0xFF89969C), Modifier.padding(top = 4.dp),
            )
        }
        when {
            uploading -> CircularProgressIndicator(color = NC.Teal, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            uploaded?.verified == true -> {
                Icon(Icons.Rounded.Check, null, tint = Color(0xFF28A178), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Txt("Verified", 11, Color(0xFF28A178), weight = FontWeight.Bold)
            }
            uploaded != null -> Txt("In review", 11, Color(0xFFD29A3A), weight = FontWeight.Bold)
            slot.required -> Txt("Required", 11, Color(0xFFD9615B), weight = FontWeight.Bold)
            else -> Txt("Optional", 11, Color(0xFF94A0A5), weight = FontWeight.Bold)
        }
    }
}

/** Asks which document is being uploaded before opening the file picker. */
@Composable
private fun DocumentTypeSheet(slots: List<DocumentSlot>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    NcSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Eyebrow("Upload document")
            Txt("Which document is this?", 21, Color(0xFF253844), Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
            Txt(
                "Choose the document, then select the file from your phone.", 12, Color(0xFF7D8C92),
                Modifier.padding(top = 8.dp, bottom = 16.dp), lineHeight = 18,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEach { slot ->
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier.fillMaxWidth().pressable(shape, pressedScale = 0.98f) { onChoose(slot.type) }
                            .background(Color.White, shape).border(1.dp, Color(0xFFE3E9E9), shape).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(slot.label, 13, Color(0xFF344750), weight = FontWeight.Bold)
                            Txt(
                                when {
                                    slot.uploaded != null -> "Uploaded · this replaces it"
                                    else -> slot.description ?: if (slot.required) "Required" else "Optional"
                                },
                                11, Color(0xFF8B989D), Modifier.padding(top = 3.dp), lineHeight = 15,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFFA5B0B4))
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewsScreen(back: () -> Unit) {
    var data by remember { mutableStateOf<ReviewsData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        error = null
        try {
            data = ReviewsRepository.load()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your reviews. Please try again."
        }
    }

    SubScreen("Reviews", "Feedback from your clients", back) {
        val d = data
        when {
            d == null && error != null -> ErrorNote(error!!, Modifier.padding(20.dp)) { reload++ }
            d == null -> Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
            else -> {
                Row(
                    Modifier.enterUp(0).padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                        .background(NC.DeepTeal, RoundedCornerShape(24.dp)).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Txt(String.format(Locale.US, "%.1f", d.average), 36, Color.White, weight = FontWeight.Bold)
                            Txt("out of 5", 11, Color.White.copy(alpha = 0.55f), Modifier.padding(start = 8.dp, bottom = 6.dp))
                        }
                        Stars(d.average.roundToInt(), 15.dp, Color(0xFFFFC857), Color.White.copy(alpha = 0.2f), Modifier.padding(top = 12.dp))
                    }
                    Box(Modifier.width(1.dp).height(56.dp).background(Color.White.copy(alpha = 0.1f)))
                    Column(Modifier.padding(start = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(d.total.toString(), 24, Color.White, weight = FontWeight.Bold)
                        Txt("Total reviews", 10, Color.White.copy(alpha = 0.55f), Modifier.padding(top = 4.dp))
                    }
                }
                Column(Modifier.enterUp(1).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 28.dp)) {
                    SectionTitle("Recent feedback")
                    if (d.reviews.isEmpty()) {
                        SoftCard(Modifier.fillMaxWidth()) {
                            Txt("No reviews yet", 14, Color(0xFF293C47), weight = FontWeight.Bold)
                            Txt("Clients can rate you after a completed booking.", 12, Color(0xFF819096), Modifier.padding(top = 4.dp))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { d.reviews.forEach { ReviewCard(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stars(filled: Int, size: Dp, on: Color, off: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { i -> Icon(Icons.Rounded.Star, null, tint = if (i <= filled) on else off, modifier = Modifier.size(size)) }
    }
}

@Composable
private fun ReviewCard(review: Review) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().background(Color.White, shape).border(1.dp, Color(0xFFE2E9E9), shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color(0xFFE4F5F2), CircleShape), contentAlignment = Alignment.Center) {
                Txt(initialsOf(review.clientName), 12, Color(0xFF178C89), weight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Txt(review.clientName, 13, Color(0xFF30434D), weight = FontWeight.Bold, maxLines = 1)
                val date = review.createdAt.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
                Txt(listOfNotNull(review.serviceName, date).joinToString(" · "), 10, Color(0xFF929EA3), Modifier.padding(top = 4.dp), maxLines = 1)
            }
            Stars(review.rating, 11.dp, Color(0xFFF5B642), Color(0xFFE2E8E8))
        }
        review.comment?.let { Txt("“$it”", 12, Color(0xFF63767F), Modifier.padding(top = 12.dp), lineHeight = 20) }
    }
}

private const val SUPPORT_EMAIL = "info@nursecenter.co.za"

private val FAQS = listOf(
    "How do I cancel a booking?" to
        "Open the booking in your calendar and contact NurseCenter support. We will help notify the client and arrange cover.",
    "When will I receive my payout?" to
        "Completed bookings are paid into your wallet within 24 hours. Weekly payouts are processed every Friday.",
    "How do I update an expired document?" to
        "Go to More, then Documents, and select Upload document. Our team will verify it as soon as possible.",
)

@Composable
fun SupportScreen(back: () -> Unit, onStartChat: () -> Unit) {
    val context = LocalContext.current
    var openFaq by rememberSaveable { mutableIntStateOf(0) }
    fun open(intent: Intent) = runCatching { context.startActivity(intent) }

    SubScreen("Help & support", "We are here when you need us", back) {
        Column(
            Modifier.enterUp(0).padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                .background(Color(0xFFE9F7F5), RoundedCornerShape(22.dp)).padding(20.dp)
        ) {
            IconTile(Icons.Outlined.HeadsetMic, Color.White, Color(0xFF188F8C), size = 44.dp, iconSize = 21.dp, corner = 14.dp)
            Txt("How can we help?", 17, Color(0xFF284149), Modifier.padding(top = 16.dp), weight = FontWeight.Bold)
            Txt(
                "Our nurse support team is available every day from 06:00 to 22:00.", 12, Color(0xFF69807F),
                Modifier.padding(top = 8.dp), lineHeight = 19,
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupportButton("Start a chat", Icons.Outlined.ChatBubbleOutline, filled = true, Modifier.weight(1f), onClick = onStartChat)
                SupportButton("Call support", Icons.Outlined.Phone, filled = false, Modifier.weight(1f)) {
                    open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE")))
                }
            }
        }
        Column(Modifier.enterUp(1).padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 28.dp)) {
            SectionTitle("Frequently asked questions")
            CardList(
                FAQS.mapIndexed { index, (question, answer) ->
                    { FaqRow(question, answer, expanded = openFaq == index) { openFaq = if (openFaq == index) -1 else index } }
                }
            )
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.padding(top = 20.dp).fillMaxWidth()
                    .pressable(shape, pressedScale = 0.98f) { open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))) }
                    .background(Color.White, shape).border(1.dp, Color(0xFFE2E9E9), shape).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt("Still need help?", 12, Color(0xFF344750), weight = FontWeight.Bold)
                Txt("Email $SUPPORT_EMAIL", 11, Color(0xFF8B979C), Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SupportButton(text: String, icon: ImageVector, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val fg = if (filled) Color.White else Color(0xFF2E5B5A)
    Row(
        modifier.pressable(shape, onClick = onClick).background(if (filled) Color(0xFF1C9F9C) else Color.White, shape).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Txt(text, 12, fg, weight = FontWeight.Bold)
    }
}

@Composable
private fun FaqRow(question: String, answer: String, expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "faqArrow")
    Column(Modifier.fillMaxWidth().pressable(RoundedCornerShape(0.dp), pressedScale = 0.99f, onClick = onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(question, 12, Color(0xFF32454F), Modifier.weight(1f), weight = FontWeight.Bold)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFF9EAAAE),
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Txt(answer, 11, Color(0xFF7A898F), Modifier.padding(top = 12.dp, end = 20.dp), lineHeight = 19)
        }
    }
}
