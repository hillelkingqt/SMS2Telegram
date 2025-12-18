package com.example.telegramforwarder.data.local

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String
)

class ContactHelper(private val context: Context) {

    fun getContactNameByNumber(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        // Simple normalization for matching
        val target = phoneNumber.replace(Regex("[^0-9+]"), "")

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0)
                }
            }
        } catch (e: Exception) {
            Log.e("ContactHelper", "Error resolving contact: ${e.message}")
        }
        return name
    }

    fun searchContacts(query: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        // Limit results to avoid memory issues with huge lists

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = it.getString(idIdx)
                    val name = it.getString(nameIdx)
                    val number = it.getString(numIdx)
                    contacts.add(Contact(id, name, number))
                }
            }
        } catch (e: Exception) {
             Log.e("ContactHelper", "Error searching contacts: ${e.message}")
        }
        return contacts.distinctBy { it.name + it.phoneNumber } // Basic dedupe
    }

    fun getAllContacts(offset: Int, limit: Int): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT $limit OFFSET $offset"

        // Note: Android ContentResolver doesn't support LIMIT/OFFSET in standard SQL way for all providers.
        // We might have to fetch all and slice, or use specific query parameters if available.
        // Or using bundle for pagination on newer APIs.
        // For broad compatibility and simplicity in this context (personal app), fetching larger chunks or simple iteration is safer.
        // However, "Limit $limit Offset $offset" string in sortOrder is a known hack that works on some Sqlite backends but isn't guaranteed in ContentProviders.
        // Let's try to just query all (projection limited) and manually slice. It's safer.

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                if (it.moveToPosition(offset)) {
                    val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    var count = 0
                    do {
                        val id = it.getString(idIdx)
                        val name = it.getString(nameIdx)
                        val number = it.getString(numIdx)
                        contacts.add(Contact(id, name, number))
                        count++
                    } while (it.moveToNext() && count < limit)
                }
            }
        } catch (e: Exception) {
             Log.e("ContactHelper", "Error fetching contacts: ${e.message}")
        }
        return contacts
    }
}
