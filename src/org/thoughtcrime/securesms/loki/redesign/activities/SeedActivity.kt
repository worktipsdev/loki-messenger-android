package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_seed.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.loki.getColorWithID
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.hexEncodedPrivateKey
import java.io.File

class SeedActivity : BaseActionBarActivity() {

    private val seed by lazy {
        val languageFileDirectory = File(applicationInfo.dataDir)
        var hexEncodedSeed = IdentityKeyUtil.retrieve(this, IdentityKeyUtil.lokiSeedKey)
        if (hexEncodedSeed == null) {
            hexEncodedSeed = IdentityKeyUtil.getIdentityKeyPair(this).hexEncodedPrivateKey // Legacy account
        }
        MnemonicCodec(languageFileDirectory).encode(hexEncodedSeed!!, MnemonicCodec.Language.Configuration.english)
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seed)
        supportActionBar!!.title = "Your Recovery Phrase"
        val seedReminderViewTitle = SpannableString("You're almost finished! 90%")
        seedReminderViewTitle.setSpan(ForegroundColorSpan(resources.getColorWithID(R.color.accent, theme)), 24, 27, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        seedReminderView.title = seedReminderViewTitle
        seedReminderView.subtitle = "Tap and hold the redacted words to reveal your recovery phrase, then store it safely to secure your Session ID."
        seedReminderView.setProgress(90, false)
        seedReminderView.hideContinueButton()
        var redactedSeed = seed
        var index = 0
        for (character in seed) {
            if (character.isLetter()) {
                redactedSeed = redactedSeed.replaceRange(index, index + 1, "▆")
            }
            index += 1
        }
        seedTextView.setTextColor(resources.getColorWithID(R.color.accent, theme))
        seedTextView.text = redactedSeed
        seedTextView.setOnLongClickListener { revealSeed(); true }
        revealButton.setOnLongClickListener { revealSeed(); true }
        copyButton.setOnClickListener { copySeed() }
    }
    // endregion

    // region Updating
    private fun revealSeed() {
        val seedReminderViewTitle = SpannableString("Account secured! 100%")
        seedReminderViewTitle.setSpan(ForegroundColorSpan(resources.getColorWithID(R.color.accent, theme)), 17, 21, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        seedReminderView.title = seedReminderViewTitle
        seedReminderView.subtitle = "Make sure to store your recovery phrase in a safe place"
        seedReminderView.setProgress(100, true)
        val seedTextViewLayoutParams = seedTextView.layoutParams as LinearLayout.LayoutParams
        seedTextViewLayoutParams.height = seedTextView.height
        seedTextView.layoutParams = seedTextViewLayoutParams
        seedTextView.setTextColor(resources.getColorWithID(R.color.text, theme))
        seedTextView.text = seed
        TextSecurePreferences.setHasViewedSeed(this, true)
    }
    // endregion

    // region Interaction
    private fun copySeed() {
        revealSeed()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Seed", seed)
        clipboard.primaryClip = clip
        Toast.makeText(this, R.string.activity_register_public_key_copied_message, Toast.LENGTH_SHORT).show()
    }
    // endregion
}