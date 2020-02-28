package org.thoughtcrime.securesms.loki.redesign.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import kotlinx.android.synthetic.main.dialog_edit_device_name.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.devicelist.Device

class EditDeviceNameDialog : DialogFragment() {
    private lateinit var contentView: View
    var device: Device? = null
    var delegate: EditDeviceNameDialogDelegate? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context!!)
        contentView = LayoutInflater.from(context!!).inflate(R.layout.dialog_edit_device_name, null)
        contentView.cancelButton.setOnClickListener { dismiss() }
        contentView.okButton.setOnClickListener { updateDeviceName() }
        builder.setView(contentView)
        val result = builder.create()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return result
    }

    private fun updateDeviceName() {
        DatabaseFactory.getLokiUserDatabase(context).setDisplayName(device!!.id, contentView.deviceNameEditText.text.toString())
        delegate?.handleDeviceNameChanged(device!!)
        dismiss()
    }
}

interface EditDeviceNameDialogDelegate {

    fun handleDeviceNameChanged(device: Device)
}