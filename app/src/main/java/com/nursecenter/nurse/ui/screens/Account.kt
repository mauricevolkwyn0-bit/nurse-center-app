package com.nursecenter.nurse.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nursecenter.nurse.ui.CardList
import com.nursecenter.nurse.ui.Eyebrow
import com.nursecenter.nurse.ui.IconBtn
import com.nursecenter.nurse.ui.IconTile
import com.nursecenter.nurse.ui.PageTitle
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

@Composable
fun MoreScreen(goTo: (Screen) -> Unit, onSignOut: () -> Unit) {
    val soon = rememberComingSoon()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Column(Modifier.enterUp(0)) {
            Eyebrow("Your account")
            PageTitle("More", Modifier.padding(top = 4.dp))
        }
        Row(
            Modifier.enterUp(1).padding(top = 20.dp).fillMaxWidth().background(NC.DeepTeal, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).background(Color(0xFF45C4B3), CircleShape), contentAlignment = Alignment.Center) {
                Txt("MV", 18, Color(0xFF14383D), weight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Txt("Maurice Volkwyn", 17, Color.White, weight = FontWeight.Bold)
                Txt("Home-Based Care Nurse", 12, Color.White.copy(alpha = 0.55f), Modifier.padding(top = 4.dp))
            }
            Row(
                Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFF8DE0C4), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Txt("Verified", 11, Color(0xFF8DE0C4), weight = FontWeight.Bold)
            }
        }
        CardList(
            listOf(
                { MenuRow(Icons.Outlined.AccountCircle, "Profile & personal details", "Contact and professional info") { goTo(Screen.Profile) } },
                { MenuRow(Icons.Outlined.TaskAlt, "Documents", "4 verified documents") { goTo(Screen.Documents) } },
                { MenuRow(Icons.Outlined.AccountBalanceWallet, "Wallet & payouts", "Balance R 3,420.00") { goTo(Screen.Wallet) } },
                { MenuRow(Icons.Outlined.StarOutline, "Reviews", "4.9 average from 28 clients") { soon("Reviews") } },
            ),
            Modifier.enterUp(2).padding(top = 20.dp),
        )
        CardList(
            listOf(
                { MenuRow(Icons.Outlined.HeadsetMic, "Help & support") { soon("Help & support") } },
                { MenuRow(Icons.Rounded.PowerSettingsNew, "Sign out", danger = true, onClick = onSignOut) },
            ),
            Modifier.enterUp(3).padding(top = 16.dp),
        )
        Txt(
            "Nurse Center Nurse Portal · v2.4.0", 11, NC.Faint,
            Modifier.fillMaxWidth().padding(vertical = 24.dp), weight = FontWeight.Medium, align = TextAlign.Center,
        )
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
    val soon = rememberComingSoon()
    SubScreen("My wallet", "Earnings & payouts", back) {
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
                    Txt("Available balance", 12, Color.White.copy(alpha = 0.55f), weight = FontWeight.SemiBold)
                    Txt(rand(countUp(3420f, 1100), cents = true), 32, Color.White, Modifier.padding(top = 8.dp), weight = FontWeight.Bold, spacing = -0.03f)
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt("Next payout", 11, Color.White.copy(alpha = 0.5f))
                            Txt("Friday, 27 Sep", 13, Color.White, Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
                        }
                        Txt(
                            "Withdraw", 12, Color(0xFF14383C),
                            Modifier.pressable(RoundedCornerShape(12.dp)) { soon("Withdrawals") }
                                .background(Color(0xFF42C5B4), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            weight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Row(Modifier.enterUp(1).padding(top = 24.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftCard(Modifier.weight(1f)) {
                Txt("This month", 12, Color(0xFF839197))
                Txt(rand(countUp(8460f)), 19, Color(0xFF263944), Modifier.padding(top = 8.dp), weight = FontWeight.Bold)
                Txt("+18% from August", 11, NC.Success, Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
            }
            SoftCard(Modifier.weight(1f)) {
                Txt("Pending", 12, Color(0xFF839197))
                Txt(rand(countUp(1600f)), 19, Color(0xFF263944), Modifier.padding(top = 8.dp), weight = FontWeight.Bold)
                Txt("2 upcoming shifts", 11, Color(0xFF94A0A5), Modifier.padding(top = 4.dp))
            }
        }
        Column(Modifier.enterUp(2).padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 28.dp)) {
            SectionTitle("Transactions", action = "View statement", onAction = { soon("Statements") })
            CardList(
                listOf(
                    { TransactionRow("Home-based care", "24 Sep 2026", "+ R 1,240") },
                    { TransactionRow("Weekly payout", "20 Sep 2026", "- R 2,800", payout = true) },
                    { TransactionRow("Wellness check", "18 Sep 2026", "+ R 620") },
                )
            )
        }
    }
}

@Composable
private fun TransactionRow(title: String, date: String, amount: String, payout: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(
            Icons.Outlined.AccountBalanceWallet,
            background = if (payout) Color(0xFFF8EEEE) else Color(0xFFE8F7F4),
            tint = if (payout) Color(0xFFC67B72) else Color(0xFF1C9792),
            size = 36.dp, iconSize = 17.dp,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Txt(title, 13, Color(0xFF31434D), weight = FontWeight.Bold)
            Txt(date, 11, Color(0xFF929EA3), Modifier.padding(top = 4.dp))
        }
        Txt(amount, 13, if (payout) Color(0xFF64767E) else Color(0xFF239C76), weight = FontWeight.Bold)
    }
}

@Composable
fun ProfileScreen(back: () -> Unit) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("Maurice Volkwyn") }
    var email by rememberSaveable { mutableStateOf("maurice@nursecenter.co.za") }
    var phone by rememberSaveable { mutableStateOf("+27 72 445 9081") }
    var speciality by rememberSaveable { mutableStateOf("Home-Based Care") }
    var area by rememberSaveable { mutableStateOf("Cape Town · within 20 km") }

    SubScreen(
        "Profile", "Personal & professional details", back,
        action = {
            Txt(
                if (editing) "Save" else "Edit", 13, Color(0xFF168E8C),
                Modifier.pressable(RoundedCornerShape(8.dp)) {
                    if (editing) Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                    editing = !editing
                }.padding(8.dp),
                weight = FontWeight.Bold,
            )
        },
    ) {
        Column(
            Modifier.enterUp(0).padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                .background(Color.White, RoundedCornerShape(22.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Box(Modifier.size(80.dp).background(Color(0xFFDFF4F0), CircleShape), contentAlignment = Alignment.Center) {
                    Txt(initialsOf(name), 22, Color(0xFF158B88), weight = FontWeight.Bold)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(32.dp)
                        .background(Color.White, CircleShape).padding(3.dp).background(Color(0xFF1AA3A1), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
            Txt(name, 18, Color(0xFF293C47), Modifier.padding(top = 12.dp), weight = FontWeight.Bold)
            Txt("SANC No. 14820937", 12, Color(0xFF859399), Modifier.padding(top = 4.dp))
        }
        Column(
            Modifier.enterUp(1).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileField("Full name", name, editing) { name = it }
            ProfileField("Email address", email, editing) { email = it }
            ProfileField("Mobile number", phone, editing) { phone = it }
            ProfileField("Speciality", speciality, editing) { speciality = it }
            ProfileField("Service area", area, editing) { area = it }
        }
    }
}

private fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
private fun ProfileField(label: String, value: String, editing: Boolean, onChange: (String) -> Unit) {
    val border by animateColorAsState(if (editing) Color(0xFF62C5BD) else Color(0xFFE1E8E8), label = "profileBorder")
    val bg by animateColorAsState(if (editing) Color.White else Color(0xFFF4F7F6), label = "profileBg")
    val shape = RoundedCornerShape(14.dp)
    Column {
        Txt(label, 12, Color(0xFF77888F), Modifier.padding(bottom = 8.dp), weight = FontWeight.Bold)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            readOnly = !editing,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Jakarta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (editing) Color(0xFF243642) else Color(0xFF4B5E67),
            ),
            cursorBrush = SolidColor(NC.Teal),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(48.dp).background(bg, shape).border(1.dp, border, shape).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

@Composable
fun DocumentsScreen(back: () -> Unit) {
    val soon = rememberComingSoon()
    SubScreen("Documents", "Credentials & compliance", back) {
        Row(
            Modifier.enterUp(0).padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth()
                .background(Color(0xFFE8F7F2), RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFF209C7B), modifier = Modifier.size(21.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Txt("Profile fully verified", 13, Color(0xFF28534A), weight = FontWeight.Bold)
                Txt("All required documents are current.", 11, Color(0xFF69827C), Modifier.padding(top = 4.dp))
            }
        }
        Column(Modifier.enterUp(1).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 28.dp)) {
            SectionTitle("Your documents")
            CardList(
                listOf(
                    { DocumentRow("SANC Registration", "Expires 31 Dec 2026") { soon("Document preview") } },
                    { DocumentRow("South African ID", "Verified 14 Mar 2026") { soon("Document preview") } },
                    { DocumentRow("Police Clearance", "Expires 08 Feb 2027") { soon("Document preview") } },
                    { DocumentRow("Nursing Qualification", "Verified 14 Mar 2026") { soon("Document preview") } },
                )
            )
            SecondaryButton("Upload document", Icons.Outlined.CloudUpload, { soon("Uploading") }, Modifier.padding(top = 20.dp).fillMaxWidth())
            Txt(
                "PDF, JPG or PNG. Your documents are encrypted and only used for verification.", 11, Color(0xFF909CA1),
                Modifier.padding(top = 16.dp).widthIn(max = 280.dp).align(Alignment.CenterHorizontally),
                lineHeight = 16, align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DocumentRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(RoundedCornerShape(0.dp), pressedScale = 0.985f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Outlined.Description, NC.TealSoft, Color(0xFF178E89))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Txt(title, 13, Color(0xFF30434D), weight = FontWeight.Bold)
            Txt(detail, 11, Color(0xFF89969C), Modifier.padding(top = 4.dp))
        }
        Icon(Icons.Rounded.Check, null, tint = Color(0xFF28A178), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Txt("Verified", 11, Color(0xFF28A178), weight = FontWeight.Bold)
    }
}
