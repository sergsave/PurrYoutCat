package com.sergsave.pocat.screens.catcard

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.Transition
import android.transition.Transition.TransitionListener
import android.view.*
import android.view.MotionEvent.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.sergsave.pocat.MyApplication
import com.sergsave.pocat.R
import com.sergsave.pocat.helpers.EventObserver
import com.sergsave.pocat.helpers.FadeOutSoundEffect
import com.sergsave.pocat.helpers.ImageUtils
import com.sergsave.pocat.helpers.SupportTransitionListenerAdapter
import com.sergsave.pocat.models.Card
import com.sergsave.pocat.vibration.RingdroidSoundBeatDetector
import com.sergsave.pocat.vibration.RythmOfSoundVibrator
import kotlinx.android.synthetic.main.fragment_purring.*

class PurringFragment : Fragment() {

    private val navigation: NavigationViewModel by activityViewModels()
    private val viewModel: PurringViewModel by viewModels {
        val card = arguments?.getParcelable<Card>(ARG_CARD)
        if (card == null)
            throw IllegalArgumentException("Need card")

        (requireActivity().application as MyApplication).appContainer
            .providePurringViewModelFactory(card)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playerTimeoutHandler: Handler? = null
    private var fadeOutEffect: FadeOutSoundEffect? = null
    private var vibrator: RythmOfSoundVibrator? = null
    private var transitionListener: TransitionListener? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onStop() {
        // Release resources for background mode
        deinitAudio()
        super.onStop()
    }

    override fun onStart() {
        // In case of restart
        super.onStart()
        initAudio(viewModel.catData.value?.purrAudioUri)
    }

    override fun onDetach() {
        // Avoid fragment leakage
        transitionListener?.let {
            requireActivity().window.sharedElementEnterTransition.removeListener(it)
        }
        super.onDetach()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_purring, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Shared element transition
        val transitionName = arguments?.getString(ARG_TRANSITION_NAME)
        photo_image.transitionName = transitionName

        photo_image.setOnTouchListener { _, event -> onTouchEvent(event) }

        setHasOptionsMenu(true)

        navigation.apply {
            backPressedEvent.observe(viewLifecycleOwner, EventObserver {
                navigation.goToBackScreen()
            })

            tutorialCompletedEvent.observe(viewLifecycleOwner, EventObserver {
                viewModel.isTutorialAchieved = true
            })
        }

        viewModel.apply {
            catData.observe(viewLifecycleOwner, Observer {
                ImageUtils.loadInto(requireContext(), it.photoUri, photo_image) { onPhotoLoaded() }
                initAudio(it.purrAudioUri)
                setTitle(it.name)
            })

            editCatEvent.observe(viewLifecycleOwner, EventObserver { id ->
                navigation.editCat(id)
            })

            menuState.observe(viewLifecycleOwner, Observer {
                requireActivity().invalidateOptionsMenu()
            })

            sharingLoaderIsVisible.observe(viewLifecycleOwner, Observer {
                requireActivity().invalidateOptionsMenu()
            })

            sharingSuccessEvent.observe(viewLifecycleOwner, EventObserver {
                startActivity(it)
            })

            snackbarMessageEvent.observe(viewLifecycleOwner, EventObserver {
                showSnackbar(resources.getString(it))
            })
        }
    }

    private fun setTitle(title: String?) {
        title?.let { (activity as? AppCompatActivity)?.supportActionBar?.title = it }
    }

    private fun onPhotoLoaded() {
        val needStartTransition = navigation.isSharedElementTransitionPostponed.value ?: false
        if (needStartTransition) {
            startPostponedTransition { showTutorialIfNeeded() }
            navigation.isSharedElementTransitionPostponed.value = false
            return
        }

        showTutorialIfNeeded()
    }

    private fun startPostponedTransition(onTransitionEndListener: () -> Unit) {
        activity?.supportStartPostponedEnterTransition()

        transitionListener = object: SupportTransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition?) {
                activity?.window?.sharedElementEnterTransition?.removeListener(this)
                onTransitionEndListener()
            }
        }

        activity?.window?.sharedElementEnterTransition?.addListener(transitionListener)
    }

    private fun showTutorialIfNeeded() {
        if (!viewModel.isTutorialAchieved)
            navigation.showTutorial()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(main_layout, message, Snackbar.LENGTH_LONG).show()
    }

    private fun isDeviceInSilentMode(): Boolean {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
    }

    private fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.action == ACTION_DOWN)
            viewModel.onTouchStarted()

        if(event.action == ACTION_UP)
            viewModel.onTouchFinished()

        if(event.action != ACTION_DOWN && event.action != ACTION_MOVE)
            return false

        if(event.action == ACTION_DOWN && isDeviceInSilentMode()) {
            showSnackbar(getString(R.string.cat_screen_popup_make_louder))
            return true
        }

        navigation.onCatPetted()
        playAudio()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(viewModel.menuId, menu)

        viewModel.menuState.value?.hidedActionIds?.forEach { menu.findItem(it)?.isVisible = false  }
        viewModel.menuState.value?.visibleActionIds?.forEach { menu.findItem(it)?.isVisible = true  }

        menu.findItem(viewModel.shareActionId)?.let {
            if(viewModel.sharingLoaderIsVisible.value == true) {
                it.setActionView(R.layout.view_loader)
                it.actionView?.setOnClickListener { viewModel.onSharingLoaderClicked() }
            }
            else
                it.setActionView(null)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if(viewModel.onActionSelected(item.itemId))
            true
        else
            super.onOptionsItemSelected(item)
    }

    private fun initAudio(audioUri: Uri?) {
        if(audioUri == null || mediaPlayer != null)
            return

        requireActivity().volumeControlStream = AudioManager.STREAM_MUSIC
        mediaPlayer = MediaPlayer.create(requireContext(), audioUri)?.apply { isLooping = true }
        fadeOutEffect = mediaPlayer?.let { FadeOutSoundEffect(it, FADE_DURATION) }

        if(!viewModel.isVibrationEnabled)
            return

        vibrator = createVibrator(requireContext(), audioUri).apply { prepareAsync() }
        vibrator?.onPrepareFinishedListener = object: RythmOfSoundVibrator.OnPrepareFinishedListener {
            override fun onSuccess() { }
            override fun onFailed() { viewModel.onVibratorCreateFailed() }
        }
    }

    private fun deinitAudio() {
        playerTimeoutHandler?.removeCallbacksAndMessages(null)
        stopAudio(withFade = false)
        vibrator?.release()
        mediaPlayer?.release()
        vibrator = null
        mediaPlayer = null
        requireActivity().volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
    }

    private fun createVibrator(context: Context, audioUri: Uri): RythmOfSoundVibrator {
        val detector = RingdroidSoundBeatDetector(context, audioUri,
            { mediaPlayer?.currentPosition }
        )
        return RythmOfSoundVibrator(context, detector)
    }

    private fun playAudio() {
        if(mediaPlayer == null)
            return

        fadeOutEffect?.stop()
        mediaPlayer?.setVolume(1f, 1f)

        if(mediaPlayer?.isPlaying == false)
            mediaPlayer?.start()

        vibrator?.start()

        playerTimeoutHandler?.removeCallbacksAndMessages(null)
        playerTimeoutHandler = Handler(Looper.getMainLooper()).apply {
            postDelayed({ stopAudio(withFade = true) }, AUDIO_TIMEOUT)
        }
    }

    private fun stopAudio(withFade: Boolean){
        val pausePlayer = { if(mediaPlayer?.isPlaying == true) mediaPlayer?.pause() }

        if (withFade)
            fadeOutEffect?.start(pausePlayer)
        else {
            fadeOutEffect?.stop()
            pausePlayer()
        }
        vibrator?.stop()
    }

    companion object {
        private const val AUDIO_TIMEOUT = 1500L
        private const val FADE_DURATION = 1000L

        private const val ARG_TRANSITION_NAME = "TransitionName"
        private const val ARG_CARD = "CatCard"

        @JvmStatic
        fun newInstance(card: Card, sharedElementTransitionName: String?) =
            PurringFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TRANSITION_NAME, sharedElementTransitionName)
                    putParcelable(ARG_CARD, card)
                }
            }
    }
}
