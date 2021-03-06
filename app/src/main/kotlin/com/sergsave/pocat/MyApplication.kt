package com.sergsave.pocat

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import com.sergsave.pocat.analytics.AnalyticsLoggingDecorator
import com.sergsave.pocat.analytics.FirebaseAnalyticsTracker
import com.sergsave.pocat.apprate.AppRateManager
import com.sergsave.pocat.billing.BillingRepository
import com.sergsave.pocat.content.ContentRepository
import com.sergsave.pocat.content.CopySavingStrategy
import com.sergsave.pocat.content.ImageResizeSavingStrategy
import com.sergsave.pocat.content.LocalFilesContentStorage
import com.sergsave.pocat.helpers.FileUtils
import com.sergsave.pocat.helpers.ViewModelFactory
import com.sergsave.pocat.logging.FirebaseCrashlyticsTree
import com.sergsave.pocat.models.Card
import com.sergsave.pocat.persistent.CatDataRepository
import com.sergsave.pocat.persistent.RoomCatDataStorage
import com.sergsave.pocat.preference.PreferenceManager
import com.sergsave.pocat.samples.CatSampleProvider
import com.sergsave.pocat.samples.SoundSampleProvider
import com.sergsave.pocat.screens.catcard.FormViewModel
import com.sergsave.pocat.screens.catcard.PurringViewModel
import com.sergsave.pocat.screens.catcard.SharingDataExtractViewModel
import com.sergsave.pocat.screens.catcard.analytics.CatCardAnalyticsHelper
import com.sergsave.pocat.screens.donate.DonateViewModel
import com.sergsave.pocat.screens.main.MainViewModel
import com.sergsave.pocat.screens.main.SamplesViewModel
import com.sergsave.pocat.screens.main.UserCatsViewModel
import com.sergsave.pocat.screens.main.analytics.MainAnalyticsHelper
import com.sergsave.pocat.screens.settings.SettingsViewModel
import com.sergsave.pocat.screens.settings.analytics.SettingsAnalyticsHelper
import com.sergsave.pocat.screens.soundselection.SamplesListViewModel
import com.sergsave.pocat.screens.soundselection.SoundSelectionViewModel
import com.sergsave.pocat.screens.soundselection.analytics.SoundSelectionAnalyticsHelper
import com.sergsave.pocat.screens.testing.TestingViewModel
import com.sergsave.pocat.sharing.FirebaseCloudSharingManager
import com.sergsave.pocat.sharing.LocalDailyQuotaStrategy
import com.sergsave.pocat.sharing.ZipDataPacker
import timber.log.Timber

// Manual dependency injection
class AppContainer(context: Context) {
    private val catDataRepo = CatDataRepository(RoomCatDataStorage(context))
    private val imageStorage = LocalFilesContentStorage(context, ImageResizeSavingStrategy(context))
    private val audioStorage = LocalFilesContentStorage(context, CopySavingStrategy(context))
    private val contentRepo = ContentRepository(imageStorage, audioStorage)
    private val preferences = PreferenceManager(context)
    private val sharingManager = FirebaseCloudSharingManager(context, ZipDataPacker(context),
        LocalDailyQuotaStrategy(context, 10, Constants.SHARING_UPLOAD_QUOTA_UNIQUE_TAG))
    private val analyticsTracker = AnalyticsLoggingDecorator(FirebaseAnalyticsTracker())
    private val soundSampleProvider = SoundSampleProvider(context)
    private val catSampleProvider = CatSampleProvider(context)
    private val billingRepo = BillingRepository(context)

    // TODO: To background
    private val fileSizeCalculator = { uri: Uri -> FileUtils.resolveContentFileSize(context, uri) }

    private val mainAnalytics = MainAnalyticsHelper(analyticsTracker)
    private val soundSelectionAnalytics = SoundSelectionAnalyticsHelper(analyticsTracker)
    private val settingsAnalytics = SettingsAnalyticsHelper(analyticsTracker)
    private val catCardAnalytics = CatCardAnalyticsHelper(analyticsTracker, fileSizeCalculator)
    private val maxAudioFileSizeMB = 2L

    fun provideMainViewModelFactory() =
        ViewModelFactory(MainViewModel::class.java, {
            MainViewModel(catDataRepo, contentRepo, sharingManager,
                preferences, billingRepo, mainAnalytics)
        })

    fun provideSamplesViewModelFactory() =
        ViewModelFactory(SamplesViewModel::class.java, {
            SamplesViewModel(catSampleProvider, mainAnalytics)
        })

    fun provideUserCatsViewModelFactory() =
        ViewModelFactory(UserCatsViewModel::class.java, {
            UserCatsViewModel(catDataRepo, mainAnalytics)
        })

    fun provideFormViewModelFactory(card: Card?) =
        ViewModelFactory(FormViewModel::class.java, {
            FormViewModel(catDataRepo, contentRepo, card, catCardAnalytics)
        })

    fun providePurringViewModelFactory(card: Card) =
        ViewModelFactory(PurringViewModel::class.java, {
            PurringViewModel(catDataRepo, sharingManager, preferences, card, catCardAnalytics)
        })

    fun provideSharingDataExtractViewModelFactory() =
        ViewModelFactory(SharingDataExtractViewModel::class.java, {
            SharingDataExtractViewModel(sharingManager, contentRepo, catCardAnalytics)
        })

    fun provideSoundSelectionViewModelFactory() =
        ViewModelFactory(SoundSelectionViewModel::class.java, {
            SoundSelectionViewModel(fileSizeCalculator, maxAudioFileSizeMB, soundSelectionAnalytics)
        })

    fun provideSamplesListViewModelFactory() =
        ViewModelFactory(SamplesListViewModel::class.java, {
            SamplesListViewModel(soundSampleProvider)
        })

    fun provideSettingsViewModelFactory() =
        ViewModelFactory(SettingsViewModel::class.java, {
            SettingsViewModel(settingsAnalytics)
        })

    fun provideDonateViewModelFactory() =
        ViewModelFactory(DonateViewModel::class.java, {
            DonateViewModel(billingRepo)
        })

    fun provideTestingViewModelFactory() =
        ViewModelFactory(TestingViewModel::class.java, {
            TestingViewModel(catDataRepo, contentRepo, preferences)
        })

    val appRateManager = AppRateManager(context)
}

class MyApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate() {
        // APK Sideloading crash prevention
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val manager = com.google.android.play.core.missingsplits.MissingSplitsManagerFactory
                .create(this)
            if (manager.disableAppIfMissingRequiredSplits())
                return
        }

        super.onCreate()

        Timber.plant(if (BuildConfig.LOG_ENABLED) Timber.DebugTree() else FirebaseCrashlyticsTree())
        Timber.i("App started. Logging should be disabled in production")
        appContainer // init
    }
}
