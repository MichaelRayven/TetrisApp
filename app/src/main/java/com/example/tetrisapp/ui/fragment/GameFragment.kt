package com.example.tetrisapp.ui.fragment

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation.findNavController
import com.example.tetrisapp.R
import com.example.tetrisapp.databinding.FragmentGameBinding
import com.example.tetrisapp.databinding.SidebarBinding
import com.example.tetrisapp.model.game.Tetris
import com.example.tetrisapp.model.game.configuration.PieceConfigurations
import com.example.tetrisapp.model.game.singleplayer.GameOverSingleplayerParcel
import com.example.tetrisapp.ui.viewmodel.GameViewModel
import com.example.tetrisapp.util.MediaPlayerUtil
import com.example.tetrisapp.util.OnGestureListener
import com.example.tetrisapp.util.OnGestureListener.GestureListener
import com.example.tetrisapp.util.OnTouchListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

@AndroidEntryPoint
open class GameFragment : Fragment() {
    protected lateinit var binding: FragmentGameBinding
    private lateinit var sidebarBinding: SidebarBinding
    protected val viewModel by viewModels<GameViewModel>()

    @Inject lateinit var mediaHelper: MediaPlayerUtil
    protected var gameMusic: MediaPlayer? = null
    private var musicVolume = 0.5f
    protected var sfxVolume = 0.5f

    private lateinit var preferences: SharedPreferences

    private val backPressCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            confirmExit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressCallback)
        
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        
        viewModel.configuration = PieceConfigurations
            .valueOf(preferences.getString(getString(R.string.setting_configuration), "DEFAULT")!!)
            .configuration
        
        viewModel.game = Tetris(
            viewModel.configuration,
            viewModel.configuration.starterPieces,
            viewModel.configuration.initialHistory
        )
        
        viewModel.countdown = preferences.getInt(getString(R.string.setting_countdown), 5)
        viewModel.countdownRemaining = viewModel.countdown
        
        musicVolume = preferences.getInt(getString(R.string.setting_music_volume), 5) / 10f
        sfxVolume = preferences.getInt(getString(R.string.setting_sfx_volume), 5) / 10f
        gameMusic = MediaPlayer.create(requireContext(), R.raw.main)
        gameMusic?.setOnPreparedListener { player -> player.pause() }
        gameMusic?.setVolume(musicVolume, musicVolume)
        gameMusic?.isLooping = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentGameBinding.inflate(inflater, container, false)
        binding.root.keepScreenOn = true
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.gameView.game = viewModel.game
        inflateSidebar()
        if (!preferences.getBoolean(getString(R.string.setting_control_scheme), false)) {
            initOnClickListeners()
        } else {
            initOnTouchListeners()
        }
        initGameListeners()
        updateScoreboard()
        updatePieceViews()
    }

    protected open fun inflateSidebar() {
        binding.stub.setOnInflateListener { _: ViewStub?, inflated: View? ->
            sidebarBinding = SidebarBinding.bind(
                inflated!!
            )
        }
        binding.stub.layoutResource = R.layout.sidebar
        binding.stub.inflate()
    }

    override fun onResume() {
        super.onResume()
        startCountdown()
    }

    override fun onPause() {
        super.onPause()
        viewModel.game.setPause(true)
        viewModel.timerFuture?.cancel(true)
        viewModel.countdownFuture?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameMusic?.stop()
        gameMusic?.release()
        gameMusic = null

        viewModel.countdownFuture?.cancel()
        viewModel.timerFuture?.cancel(true)
        viewModel.futureMoveLeft?.cancel(true)
        viewModel.futureMoveRight?.cancel(true)
        viewModel.game.stop()
    }

    @SuppressLint("SetTextI18n")
    protected open fun updateScoreboard() {
        requireActivity().runOnUiThread {
            sidebarBinding.score.text = viewModel.game.score.toString()
            sidebarBinding.level.text = viewModel.game.level.toString()
            sidebarBinding.lines.text = viewModel.game.lines.toString()
            sidebarBinding.combo.text = viewModel.game.combo.toString()
        }
        try {
            val normalizedSpeed = (Tetris.DEFAULT_SPEED - viewModel.game.speed).toFloat() / (Tetris.DEFAULT_SPEED - Tetris.MIN_SPEED).toFloat()
            val roundedSpeed = round(normalizedSpeed * 10) / 10

            val params = PlaybackParams()
            params.speed = (2f - (1f - roundedSpeed))
            gameMusic?.playbackParams = params
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage ?: "")
        }
    }

    protected open fun updatePieceViews() {
        requireActivity().runOnUiThread {
            sidebarBinding.pvNext1.setPiece(viewModel.configuration[viewModel.game.tetrominoSequence[0]].copy())
            sidebarBinding.pvNext2.setPiece(viewModel.configuration[viewModel.game.tetrominoSequence[1]].copy())
            sidebarBinding.pvNext3.setPiece(viewModel.configuration[viewModel.game.tetrominoSequence[2]].copy())
            sidebarBinding.pvNext4.setPiece(viewModel.configuration[viewModel.game.tetrominoSequence[3]].copy())
            viewModel.game.heldPiece?.let { piece ->
                sidebarBinding.pvHold.setPiece(viewModel.configuration[piece].copy())
            }
        }
    }

    protected open fun initGameListeners() {
        viewModel.game.onGameValuesUpdateCallback = {
            updateScoreboard()
        }
        viewModel.game.onHoldCallback = {
            updatePieceViews()
        }
        viewModel.game.onMoveCallback = {
            binding.gameView.postInvalidate()
        }
        viewModel.game.onSolidifyCallback = {
            mediaHelper.playSound(R.raw.solidify, sfxVolume)
            updatePieceViews()
        }
        viewModel.game.onGameOverCallback = {
            onGameOver()
        }
        viewModel.game.onPauseCallback = {
            gameMusic?.pause()
        }
        viewModel.game.onResumeCallback = {
            gameMusic?.start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun initOnTouchListeners() {
        binding.gameView.post {
            binding.gameView.setOnTouchListener(object : View.OnTouchListener {
                private val width = binding.gameView.width
                private val blockSize = width / 10
                private var x = -1f
                private var col = -1
                private var moved = false
                private val SWIPE_DISTANCE_THRESHOLD = 200
                private val SWIPE_VELOCITY_THRESHOLD = 400
                private val gestureDetector =
                    GestureDetector(requireContext(), object : GestureListener() {
                        override fun onSwipe(
                            direction: Direction,
                            distance: Float,
                            velocity: Float
                        ): Boolean {
                            if (distance >= SWIPE_DISTANCE_THRESHOLD && velocity >= SWIPE_VELOCITY_THRESHOLD) {
                                when (direction) {
                                    Direction.up -> viewModel.game.hold()
                                    Direction.down -> viewModel.game.setSoftDrop(true)
                                    else -> return false
                                }
                                return true
                            }
                            return false
                        }

                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            viewModel.game.hardDrop()
                            return true
                        }
                    })

                @SuppressLint("ClickableViewAccessibility")
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val action = event.actionMasked
                    val index = event.actionIndex
                    val x = event.getX(index)
                    when (action) {
                        MotionEvent.ACTION_DOWN -> {
                            col = viewModel.game.currentPiece!!.col
                            this.x = x
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val xDiff = abs(this.x - x)
                            if (xDiff > blockSize / 2f) moved = true
                            if (moved) {
                                var col = viewModel.game.currentPiece!!.col
                                val colDiff = ((this.x - x) / blockSize).roundToInt()
                                val desiredCol = this.col - colDiff
                                if (desiredCol == col) return true
                                while (desiredCol != col) {
                                    if (desiredCol > col) {
                                        viewModel.game.moveTetrominoRight()
                                    } else {
                                        viewModel.game.moveTetrominoLeft()
                                    }
                                    if (col == viewModel.game.currentPiece!!.col) break
                                    col = viewModel.game.currentPiece!!.col
                                }
                                return true
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!moved) {
                                if (x > width / 3f * 2) {
                                    viewModel.game.rotateTetrominoRight()
                                    return true
                                } else if (x < width / 3f) {
                                    viewModel.game.rotateTetrominoLeft()
                                    return true
                                }
                            }
                            moved = false
                        }
                    }
                    gestureDetector.onTouchEvent(event)
                    return true
                }
            })
        }
        initPauseOnClickListener()
        initSidebarOnClickListeners()
        binding.controls.visibility = View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun initOnClickListeners() {
        binding.btnLeft.setOnTouchListener(object : OnGestureListener(
            context
        ) {
            override fun onTapDown() {
                viewModel.futureMoveLeft = viewModel.executor.scheduleAtFixedRate(
                    MoveLeftRunnable(),
                    0,
                    MOVEMENT_INTERVAL.toLong(),
                    TimeUnit.MILLISECONDS
                )
                mediaHelper.playSound(R.raw.click, sfxVolume)
            }

            override fun onTapUp() {
                if (viewModel.futureMoveLeft != null) viewModel.futureMoveLeft!!.cancel(true)
            }
        })
        binding.btnRight.setOnTouchListener(object : OnGestureListener(
            context
        ) {
            override fun onTapDown() {
                viewModel.futureMoveRight = viewModel.executor.scheduleAtFixedRate(
                    MoveRightRunnable(),
                    0,
                    MOVEMENT_INTERVAL.toLong(),
                    TimeUnit.MILLISECONDS
                )
                mediaHelper.playSound(R.raw.click, sfxVolume)
            }

            override fun onTapUp() {
                if (viewModel.futureMoveRight != null) viewModel.futureMoveRight!!.cancel(true)
            }
        })
        binding.btnRotateLeft.setOnClickListener {
            viewModel.game.rotateTetrominoLeft()
            mediaHelper.playSound(R.raw.click, sfxVolume)
        }
        binding.btnRotateRight.setOnClickListener {
            viewModel.game.rotateTetrominoRight()
            mediaHelper.playSound(R.raw.click, sfxVolume)
        }
        binding.btnDown.setOnTouchListener(object : OnGestureListener(
            context
        ) {
            override fun onDoubleTap() {
                viewModel.game.hardDrop()
            }

            override fun onTapDown() {
                viewModel.game.setSoftDrop(true)
                mediaHelper.playSound(R.raw.click, sfxVolume)
            }

            override fun onTapUp() {
                viewModel.game.setSoftDrop(false)
            }
        })
        initPauseOnClickListener()
        initSidebarOnClickListeners()
    }

    protected open fun initPauseOnClickListener() {
        binding.btnPause.setOnClickListener(OnTouchListener(requireActivity()) {
            findNavController(binding.root).navigate(R.id.action_gameFragment_to_pauseFragment)
        })
    }

    protected open fun initSidebarOnClickListeners() {
        sidebarBinding.cvHold.setOnClickListener(OnTouchListener(requireActivity()) {
            viewModel.game.hold()
        })
    }

    protected open fun confirmExit() {
        viewModel.countdownFuture?.cancel()
        viewModel.timerFuture?.cancel(true)
        binding.tvCountdown.visibility = View.GONE
        viewModel.game.setPause(true)

        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(getString(R.string.exit_dialog_title))
            .setMessage(getString(R.string.exit_dialog_description))
            .setOnDismissListener { startCountdown() }
            .setNegativeButton(getString(R.string.disagree)) { _, _ -> }
            .setPositiveButton(getString(R.string.agree)) { _, _ ->
                findNavController(
                    binding.root
                ).navigate(R.id.action_gameFragment_to_mainMenuFragment)
            }
            .show()
    }

    private fun onGameOver() {
        gameMusic?.stop()
        gameMusic?.release()
        gameMusic = null
        mediaHelper.playSound(R.raw.gameover, sfxVolume)
        requireActivity().runOnUiThread {
            val action = GameFragmentDirections.actionGameFragmentToGameOverFragment(
                GameOverSingleplayerParcel(
                    score = viewModel.game.score,
                    level = viewModel.game.level,
                    lines = viewModel.game.lines,
                    timer = viewModel.timer
                )
            )
            findNavController(binding.root).navigate(action)
        }
    }

    // Runnables
    private inner class MoveRightRunnable : Runnable {
        override fun run() {
            viewModel.game.moveTetrominoRight()
        }
    }

    private inner class MoveLeftRunnable : Runnable {
        override fun run() {
            viewModel.game.moveTetrominoLeft()
        }
    }

    private fun startCountdown() {
        viewModel.countdownRemaining = viewModel.countdown

        viewModel.countdownFuture = lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                binding.tvCountdown.visibility = View.VISIBLE
                updateCountdown()
            }
            delay(1000)

            for (i in viewModel.countdownRemaining downTo 0) {
                withContext(Dispatchers.Main) {
                    updateCountdown()
                }
                delay(1000)
            }

            withContext(Dispatchers.Main) {
                binding.tvCountdown.visibility = View.GONE
                viewModel.game.setPause(false)
                startTimer()
            }
        }
    }

    private fun updateCountdown() {
        val volume = preferences.getInt(getString(R.string.setting_sfx_volume), 5) / 10f

        animateCountdownStep(binding.tvCountdown)
        if (viewModel.countdownRemaining > 0) {
            binding.tvCountdown.text = viewModel.countdownRemaining.toString()
            mediaHelper.playSound(R.raw.countdown, volume)
        } else {
            viewModel.countdownRemaining = viewModel.countdown
            binding.tvCountdown.text = "GO!"
            mediaHelper.playSound(R.raw.gamestart, volume)
        }
        viewModel.countdownRemaining--
    }

    protected open fun startTimer() {
        viewModel.timerFuture = viewModel.executor.scheduleAtFixedRate({viewModel.timer += 1}, 0, 1, TimeUnit.SECONDS)
    }

    private fun animateCountdownStep(view: View) {
        val alphaAnimator = ValueAnimator.ofFloat(1f, 0f)
        alphaAnimator.duration = 1000
        alphaAnimator.addUpdateListener { alpha: ValueAnimator ->
            view.alpha = (alpha.animatedValue as Float)
        }
        val scaleAnimator = ValueAnimator.ofFloat(1f, 1.5f)
        scaleAnimator.duration = 1000
        scaleAnimator.addUpdateListener { scale: ValueAnimator ->
            view.scaleX = (scale.animatedValue as Float)
            view.scaleY = (scale.animatedValue as Float)
        }
        alphaAnimator.start()
        scaleAnimator.start()
    }

    companion object {
        private const val TAG = "GameFragment"
        private const val MOVEMENT_INTERVAL = 125
    }
}