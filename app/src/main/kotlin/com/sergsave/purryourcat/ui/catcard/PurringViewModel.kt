package com.sergsave.purryourcat.ui.catcard

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sergsave.purryourcat.persistent.CatDataRepository
import com.sergsave.purryourcat.helpers.DisposableViewModel
import com.sergsave.purryourcat.helpers.Event
import com.sergsave.purryourcat.models.Card
import com.sergsave.purryourcat.models.CatData
import com.sergsave.purryourcat.preference.PreferenceManager
import com.sergsave.purryourcat.sharing.Pack
import com.sergsave.purryourcat.sharing.WebSharingManager
import com.sergsave.purryourcat.R

class PurringViewModel(
    private val catDataRepository: CatDataRepository,
    private val sharingManager: WebSharingManager,
    private val preferences: PreferenceManager,
    private var card: Card
) : DisposableViewModel() {

    val menuId = R.menu.menu_purring
    val shareActionId = R.id.action_share

    data class MenuState(val visibleActionIds: List<Int>,
                         val hidedActionIds: List<Int>)

    private val _catData = MutableLiveData<CatData>()
    val catData: LiveData<CatData>
        get() = _catData

    private val _menuState = MutableLiveData<MenuState>()
    val menuState: LiveData<MenuState>
        get() = _menuState

    private val _dataSavedEvent = MutableLiveData<Event<Unit>>()
    val dataSavedEvent: LiveData<Event<Unit>>
        get() = _dataSavedEvent

    private val _sharingLoaderIsVisible = MutableLiveData<Boolean>()
    val sharingLoaderIsVisible: LiveData<Boolean>
        get() = _sharingLoaderIsVisible

    private val _sharingFailedStringIdEvent = MutableLiveData<Event<Int>>()
    val sharingFailedStringIdEvent: LiveData<Event<Int>>
        get() = _sharingFailedStringIdEvent

    // Use intent is safe here because we don't save reference to any context.
    private val _sharingSuccessEvent = MutableLiveData<Event<Intent>>()
    val sharingSuccessEvent: LiveData<Event<Intent>>
        get() = _sharingSuccessEvent

    private val _editCatEvent = MutableLiveData<Event<Card>>()
    val editCatEvent: LiveData<Event<Card>>
        get() = _editCatEvent

    init {
        _catData.value = card.data
        updateMenu(card.persistentId != null)
    }

    private fun updateMenu(isCatSaved: Boolean) {
        val actions = mutableListOf<Int>()
        if (isCatSaved)
            actions += R.id.action_edit

        if (isCatSaved && card.isShareable)
            actions += R.id.action_share

        if (isCatSaved.not() && card.isSaveable)
            actions += R.id.action_save

        setVisibleMenuItems(actions.toList())
    }

    private fun setVisibleMenuItems(ids: List<Int>) {
        val allIds = listOf(R.id.action_edit, R.id.action_save, R.id.action_share)
        val hidedIds = allIds - ids
        _menuState.value = MenuState(ids, hidedIds)
    }

    private fun onSharePressed() {
        val pack = _catData.value?.let { Pack(it) }
        val single = pack?.let { sharingManager.upload(it) }
        if(single == null)
            return

        _sharingLoaderIsVisible.value = true

        val handleError = { throwable: Throwable ->
            val stringId = when (throwable) {
                is WebSharingManager.NoConnectionException -> R.string.connection_error
                else -> R.string.general_sharing_error
            }
            _sharingFailedStringIdEvent.value = Event(stringId)
        }

        val disposable = single
            .doOnEvent{ _,_ -> _sharingLoaderIsVisible.value = false }
            .subscribe(
                { data -> _sharingSuccessEvent.value = Event(data) },
                { handleError(it) }
            )

        addDisposable(disposable)
    }

    private fun onSavePressed() {
        val disposable = catDataRepository.add(card.data).subscribe(
            { id ->
                card = card.copy(persistentId = id)
                updateMenu(true)
                _dataSavedEvent.value = Event(Unit)
            },
            { Log.e("PurringViewModel", "Save failed", it) }
        )
        addDisposable(disposable)
    }

    private fun onEditPressed() {
        if(card.persistentId != null)
            _editCatEvent.value = Event(card)
    }

    fun onActionSelected(id: Int): Boolean {
        when(id) {
            R.id.action_save -> onSavePressed()
            R.id.action_share -> onSharePressed()
            R.id.action_edit -> onEditPressed()
            else -> return false
        }
        return true
    }

    val isVibrationEnabled: Boolean
        get() = preferences.isVibrationEnabled

    var isTutorialAchieved: Boolean
        get() = preferences.isPurringTutorialAchieved
        set(value) { preferences.isPurringTutorialAchieved = value }
}