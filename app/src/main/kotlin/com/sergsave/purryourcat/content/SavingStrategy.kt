package com.sergsave.purryourcat.content

import android.net.Uri
import io.reactivex.rxjava3.core.Completable
import java.io.File

interface SavingStrategy {
    fun save(sourceContent: Uri, outputFile: File): Completable
}