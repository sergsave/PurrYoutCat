@file:UseSerializers(com.sergsave.pocat.helpers.UriSerializer::class)
package com.sergsave.pocat.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
@Parcelize
data class CatData(
    val name: String? = null,
    val photoUri: Uri? = null,
    val purrAudioUri: Uri? = null
) : Parcelable