package com.example.pdftoolkit.core.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider

/** Saved output ko kisi doosri app mein kholna / share karna. */
object OutputActions {

    fun uriFor(context: Context, record: OutputRecord): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", record.file)

    fun open(context: Context, record: OutputRecord) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, record), record.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, record: OutputRecord) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(record.mime)
            .putExtra(Intent.EXTRA_STREAM, uriFor(context, record))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(send, record.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}