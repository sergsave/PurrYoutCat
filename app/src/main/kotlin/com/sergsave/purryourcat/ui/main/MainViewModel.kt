package com.sergsave.purryourcat.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.util.Log
import com.sergsave.purryourcat.content.ContentRepository
import com.sergsave.purryourcat.persistent.CatDataRepository
import com.sergsave.purryourcat.helpers.DisposableViewModel
import com.sergsave.purryourcat.helpers.Event
import com.sergsave.purryourcat.models.extractContent
import com.sergsave.purryourcat.preference.PreferenceManager
import com.sergsave.purryourcat.sharing.SharingManager
import com.sergsave.purryourcat.R
import io.reactivex.Observable
import io.reactivex.rxkotlin.Flowables

class MainViewModel(
    private val catDataRepository: CatDataRepository,
    private val contentRepository: ContentRepository,
    private val sharingManager: SharingManager,
    private val preferences: PreferenceManager
): DisposableViewModel() {

    private var pagePosition: Int? = null

    private val tabInfo2tag = mapOf<TabInfo, String>(
        TabInfo.SAMPLES to "samples",
        TabInfo.USER_CATS to "user_cats"
    )

    private val tag2tabInfo = tabInfo2tag.map { Pair(it.value, it.key) }.toMap()

    enum class TabInfo(val pageNumber: Int, val titleStringId: Int) {
        SAMPLES(0, R.string.samples_tab),
        USER_CATS(1, R.string.user_cats_tab)
    }

    private val _clearSelectionEvent = MutableLiveData<Event<Unit>>()
    val clearSelectionEvent: LiveData<Event<Unit>>
        get() = _clearSelectionEvent

    private val _requestPageChangeEvent = MutableLiveData<Event<Int>>()
    val requestPageChangeEvent: LiveData<Event<Int>>
        get() = _requestPageChangeEvent

    init {
        val tabInfo = preferences.lastTabTag?.let { tag2tabInfo.get(it) }
        tabInfo?.let { _requestPageChangeEvent.value = Event(it.pageNumber) }
    }

    fun tabInfoForPosition(position: Int): TabInfo? {
        return tabInfo2tag.keys.find { it.pageNumber == position }
    }

    fun cleanUnusedFiles() {
        addDisposable(sharingManager.cleanup().subscribe({}, { Log.e(TAG, "Cleanup failed", it) }))
        cleanUpUnusedContent()
    }

    fun onPageChanged(position: Int) {
        if(position != pagePosition)
            _clearSelectionEvent.value = Event(Unit)

        pagePosition = position
        val tabInfo = tabInfoForPosition(position)
        val tag = tabInfo?.let { tabInfo2tag.get(it) }
        tag?.let { preferences.lastTabTag = it }
    }

    fun onForwardIntent() {
        _requestPageChangeEvent.value = Event(TabInfo.USER_CATS.pageNumber)
    }

    private fun cleanUpUnusedContent() {
        val disposable = Flowables.zip(catDataRepository.read(), contentRepository.read())
            .take(1)
            .flatMap { (data, content) ->
                val usedContent = data.flatMap { (_, cat) -> cat.data.extractContent() }
                val unusedContent = content - usedContent

                Observable.fromIterable(unusedContent)
                    .concatMapCompletable { contentRepository.remove(it) }
                    .toFlowable<Unit>()
            }
            .subscribe({}, { Log.e(TAG, "Cleanup failed", it) })
        addDisposable(disposable)
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
