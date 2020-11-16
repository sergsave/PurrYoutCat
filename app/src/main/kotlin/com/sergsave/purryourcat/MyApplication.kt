package com.sergsave.purryourcat

import android.app.Application
import android.content.Context
import com.sergsave.purryourcat.content.ContentRepository
import com.sergsave.purryourcat.content.CopySavingStrategy
import com.sergsave.purryourcat.content.ImageResizeSavingStrategy
import com.sergsave.purryourcat.content.LocalFilesContentStorage
import com.sergsave.purryourcat.persistent.CatDataRepository
import com.sergsave.purryourcat.persistent.RoomCatDataStorage
import com.sergsave.purryourcat.helpers.ViewModelFactory
import com.sergsave.purryourcat.preference.PreferenceManager
import com.sergsave.purryourcat.samples.CatSampleProvider
import com.sergsave.purryourcat.samples.SoundSampleProvider
import com.sergsave.purryourcat.sharing.FirebaseCloudSharingManager
import com.sergsave.purryourcat.sharing.WebSharingManager
import com.sergsave.purryourcat.sharing.ZipDataPacker
import com.sergsave.purryourcat.models.Card
import com.sergsave.purryourcat.ui.catcard.FormViewModel
import com.sergsave.purryourcat.ui.catcard.PurringViewModel
import com.sergsave.purryourcat.ui.catcard.SharingDataExtractViewModel
import com.sergsave.purryourcat.ui.main.MainViewModel
import com.sergsave.purryourcat.ui.main.UserCatsViewModel
import com.sergsave.purryourcat.ui.main.SamplesViewModel
import com.sergsave.purryourcat.ui.testing.TestingViewModel
import com.sergsave.purryourcat.ui.soundselection.SoundSelectionViewModel

// Manual dependency injection
class AppContainer(private val context: Context) {
    private val catDataRepo = CatDataRepository(RoomCatDataStorage(context))
    private val imageStorage = LocalFilesContentStorage(context, ImageResizeSavingStrategy(context))
    private val audioStorage = LocalFilesContentStorage(context, CopySavingStrategy(context))
    private val contentRepo = ContentRepository(imageStorage, audioStorage)
    private val preferences = PreferenceManager(context)
    private val maxAudioFileSizeMB = 2L

    private val sharingManager: WebSharingManager =
         FirebaseCloudSharingManager(context, ZipDataPacker(context))

    val soundSampleProvider = SoundSampleProvider(context)

    fun provideMainViewModelFactory() =
        ViewModelFactory(MainViewModel::class.java, {
            MainViewModel(catDataRepo, contentRepo, sharingManager, preferences)
        })

    fun provideSamplesViewModelFactory() =
        ViewModelFactory(SamplesViewModel::class.java, {
            SamplesViewModel(CatSampleProvider(context))
        })

    fun provideUserCatsViewModelFactory() =
        ViewModelFactory(UserCatsViewModel::class.java, {
            UserCatsViewModel(catDataRepo)
        })

    fun provideFormViewModelFactory(card: Card?) =
        ViewModelFactory(FormViewModel::class.java, {
            FormViewModel(catDataRepo, contentRepo, card)
        })

    fun providePurringViewModelFactory(card: Card) =
        ViewModelFactory(PurringViewModel::class.java, {
            PurringViewModel(catDataRepo, sharingManager, preferences, card)
        })

    fun provideSharingDataExtractViewModelFactory() =
        ViewModelFactory(SharingDataExtractViewModel::class.java, {
            SharingDataExtractViewModel(sharingManager, contentRepo)
        })

    fun provideSoundSelectionViewModelFactory() =
        ViewModelFactory(SoundSelectionViewModel::class.java, {
            SoundSelectionViewModel(context, maxAudioFileSizeMB)
        })

    fun provideTestingViewModelFactory() =
        ViewModelFactory(TestingViewModel::class.java, {
            TestingViewModel(catDataRepo, contentRepo)
        })
}

class MyApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate() {
        super.onCreate()

        appContainer // init
    }
}
