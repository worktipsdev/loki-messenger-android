package org.thoughtcrime.securesms.loki.redesign.dialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.widget.Toast
import kotlinx.android.synthetic.main.dialog_seed.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.hexEncodedPrivateKey
import java.io.File

class SeedDialog : DialogFragment() {

    private val seed by lazy {
        val languageFileDirectory = File(context!!.applicationInfo.dataDir)
        var hexEncodedSeed = IdentityKeyUtil.retrieve(context!!, IdentityKeyUtil.lokiSeedKey)
        if (hexEncodedSeed == null) {
            hexEncodedSeed = IdentityKeyUtil.getIdentityKeyPair(context!!).hexEncodedPrivateKey // Legacy account
        }
        MnemonicCodec(languageFileDirectory).encode(hexEncodedSeed!!, MnemonicCodec.Language.Configuration.english)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context!!)
        val contentView = LayoutInflater.from(context!!).inflate(R.layout.dialog_seed, null)
        contentView.seedTextView.text = seed
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.copyButton.setOnClickListener { copySeed() }
        builder.setView(contentView)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return result
    }

    private fun copySeed() {
        val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Seed", seed)
        clipboard.primaryClip = clip
        Toast.makeText(context!!, R.string.activity_register_public_key_copied_message, Toast.LENGTH_SHORT).show()
        dismiss()
    }
}