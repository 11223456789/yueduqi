package com.peiyu.reader.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseService
import com.peiyu.reader.constant.AppConst
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.constant.EventBus
import com.peiyu.reader.constant.IntentAction
import com.peiyu.reader.constant.NotificationId
import com.peiyu.reader.constant.Status
import com.peiyu.reader.help.MediaHelp
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.help.coroutine.Coroutine
import com.peiyu.reader.help.exoplayer.ExoPlayerHelper
import com.peiyu.reader.help.glide.ImageLoader
import com.peiyu.reader.model.AudioPlay
import com.peiyu.reader.model.analyzeRule.AnalyzeUrl
import com.peiyu.reader.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import com.peiyu.reader.receiver.MediaButtonReceiver
import com.peiyu.reader.ui.book.audio.AudioPlayActivity
import com.peiyu.reader.utils.activityPendingIntent
import com.peiyu.reader.utils.broadcastPendingIntent
import com.peiyu.reader.utils.postEvent
import com.peiyu.reader.utils.printOnDebug
import com.peiyu.reader.utils.servicePendingIntent
import com.peiyu.reader.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager
import splitties.systemservices.wifiManager

/**
 * 音频播放服务
 */
class AudioPlayService : BaseService(),
    AudioManager.OnAudioFocusChangeListener,
    Player.Listener {

    companion object {
        @JvmStatic
        var isRun = false
            private set

        @JvmStatic
        var pause = true
            private set

        @JvmStatic
        var timeMinute: Int = 0

        var url: String = ""
            private set

        private const val MEDIA_SESSION_ACTIONS = (PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SEEK_TO)

        private const val APP_ACTION_STOP = "Stop"
        private const val APP_ACTION_TIMER = "Timer"
    }

    private val useWakeLock = AppConfig.audioPlayUseWakeLock
    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:AudioPlayService")
            .apply {
                this.setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")?.apply {
            setReferenceCounted(false)
        }
    }
    private val mFocusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayerHelper.createHttpExoPlayer(this)
    }
    private var mediaSessionCompat: MediaSessionCompat? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var needResumeOnAudioFocusGain = false
    private var position = AudioPlay.book?.durChapterPos ?: 0
    private var dsJob: Job? = null
    private var upNotificationJob: Coroutine<*>? = null
    private var upPlayProgressJob: Job? = null
    private var playSpeed: Float = 1f
    private var cover: Bitmap =
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.icon_read_book)

    override fun onCreate() {
        super.onCreate()
        isRun = true
        exoPlayer.addListener(this)
        AudioPlay.registerService(this)
        initMediaSession()
        initBroadcastReceiver()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        doDs()
        execute {
            ImageLoader
                .loadBitmap(this@AudioPlayService, AudioPlay.book?.getDisplayCover())
                .submit()
                .get()
        }.onSuccess {
            if (it.width > 16 && it.height > 16) {
                cover = it
                upMediaMetadata()
                upAudioPlayNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.play -> {
                    exoPlayer.stop()
                    upPlayProgressJob?.cancel()
                    pause = false
                    position = AudioPlay.book?.durChapterPos ?: 0
                    url = AudioPlay.durPlayUrl
                    play()
                }

                IntentAction.playNew -> {
                    exoPlayer.stop()
                    upPlayProgressJob?.cancel()
                    pause = false
                    position = 0
                    url = AudioPlay.durPlayUrl
                    play()
                }

                IntentAction.stopPlay -> {
                    exoPlayer.stop()
                    upPlayProgressJob?.cancel()
                    AudioPlay.status = Status.STOP
                    postEvent(EventBus.AUDIO_STATE, Status.STOP)
                }

                IntentAction.pause -> pause()
                IntentAction.resume -> resume()
                IntentAction.prev -> AudioPlay.prev()
                IntentAction.next -> AudioPlay.next()
                IntentAction.adjustSpeed -> upSpeed(intent.getFloatExtra("adjust", 1f))
                IntentAction.addTimer -> addTimer()
                IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
                IntentAction.adjustProgress -> {
                    adjustProgress(intent.getIntExtra("position", position))
                }

                IntentAction.stop -> stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        isRun = false
        abandonFocus()
        exoPlayer.release()
        mediaSessionCompat?.release()
        unregisterReceiver(broadcastReceiver)
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        AudioPlay.status = Status.STOP
        postEvent(EventBus.AUDIO_STATE, Status.STOP)
        AudioPlay.unregisterService()
        upNotificationJob?.invokeOnCompletion {
            notificationManager.cancel(NotificationId.AudioPlayService)
        }
    }

    /**
     * 播放音频
     */
    @SuppressLint("WakelockTimeout")
    private fun play() {
        if (useWakeLock) {
            wakeLock.acquire()
            wifiLock?.acquire()
        }
        upAudioPlayNotification()
        if (!requestFocus()) {
            return
        }
        execute(context = Main) {
            AudioPlay.status = Status.STOP
            postEvent(EventBus.AUDIO_STATE, Status.STOP)
            upPlayProgressJob?.cancel()
            val analyzeUrl = AnalyzeUrl(
                url,
                source = AudioPlay.bookSource,
                ruleData = AudioPlay.book,
                chapter = AudioPlay.durChapter,
                coroutineContext = coroutineContext
            )
            exoPlayer.setMediaItem(analyzeUrl.getMediaItem())
            exoPlayer.playWhenReady = true
            exoPlayer.seekTo(position.toLong())
            exoPlayer.prepare()
        }.onError {
            AppLog.put("播放出错\n${it.localizedMessage}", it)
            toastOnUi("$url ${it.localizedMessage}")
            stopSelf()
        }
    }

    /**
     * 暂停播放
     */
    private fun pause(abandonFocus: Boolean = true) {
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        try {
            pause = true
            if (abandonFocus) {
                abandonFocus()
            }
            upPlayProgressJob?.cancel()
            position = exoPlayer.currentPosition.toInt()
            if (exoPlayer.isPlaying) exoPlayer.pause()
            upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            AudioPlay.status = Status.PAUSE
            postEvent(EventBus.AUDIO_STATE, Status.PAUSE)
            upAudioPlayNotification()
        } catch (e: Exception) {
            e.printOnDebug()
        }
    }

    /**
     * 恢复播放
     */
    @SuppressLint("WakelockTimeout")
    private fun resume() {
        if (useWakeLock) {
            wakeLock.acquire()
            wifiLock?.acquire()
        }
        try {
            pause = false
            if (url.isEmpty()) {
                AudioPlay.loadOrUpPlayUrl()
                return
            }
            if (!exoPlayer.isPlaying) {
                exoPlayer.play()
            }
            upPlayProgress()
            upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            AudioPlay.status = Status.PLAY
            postEvent(EventBus.AUDIO_STATE, Status.PLAY)
            upAudioPlayNotification()
        } catch (e: Exception) {
            e.printOnDebug()
            stopSelf()
        }
    }

    /**
     * 调节进度
     */
    private fun adjustProgress(position: Int) {
        this.position = position
        exoPlayer.seekTo(position.toLong())
    }

    /**
     * 调节速度
     */
    @SuppressLint(value = ["ObsoleteSdkInt"])
    private fun upSpeed(adjust: Float) {
        kotlin.runCatching {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                playSpeed += adjust
                exoPlayer.setPlaybackSpeed(playSpeed)
                postEvent(EventBus.AUDIO_SPEED, playSpeed)
            }
        }
    }

    /**
     * 播放状态监�?     */
    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_IDLE -> {
                // 空闲
            }

            Player.STATE_BUFFERING -> {
                // 缓冲�?            }

            Player.STATE_READY -> {
                // 准备�?                AudioPlay.upLoading(false)
                if (exoPlayer.playWhenReady) {
                    AudioPlay.status = Status.PLAY
                    postEvent(EventBus.AUDIO_STATE, Status.PLAY)
                } else {
                    AudioPlay.status = Status.PAUSE
                    postEvent(EventBus.AUDIO_STATE, Status.PAUSE)
                }
                postEvent(EventBus.AUDIO_SIZE, exoPlayer.duration.toInt())
                upMediaMetadata()
                upPlayProgress()
                AudioPlay.saveDurChapter(exoPlayer.duration)
            }

            Player.STATE_ENDED -> {
                // 结束
                upPlayProgressJob?.cancel()
                AudioPlay.playPositionChanged(exoPlayer.duration.toInt())
                AudioPlay.next()
            }
        }
        upAudioPlayNotification()
    }

    private fun upMediaMetadata() {
        val metadata = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cover)
            .putText(MediaMetadataCompat.METADATA_KEY_TITLE, AudioPlay.durChapter?.title ?: "null")
            .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, AudioPlay.book?.name ?: "null")
            .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, AudioPlay.book?.author ?: "null")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, exoPlayer.duration)
            .build()
        mediaSessionCompat?.setMetadata(metadata)
    }

    /**
     * 播放错误事件
     */
    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        AudioPlay.status = Status.STOP
        postEvent(EventBus.AUDIO_STATE, Status.STOP)
        AudioPlay.upLoading(false)
        val errorMsg = "音频播放出错\n${error.errorCodeName} ${error.errorCode}"
        AppLog.put(errorMsg, error)
        toastOnUi(errorMsg)
    }

    private fun setTimer(minute: Int) {
        timeMinute = minute
        doDs()
    }

    private fun addTimer() {
        if (timeMinute == 180) {
            timeMinute = 0
        } else {
            timeMinute += 10
            if (timeMinute > 180) timeMinute = 180
        }
        doDs()
    }

    /**
     * 定时
     */
    private fun doDs() {
        postEvent(EventBus.AUDIO_DS, timeMinute)
        upAudioPlayNotification()
        dsJob?.cancel()
        dsJob = lifecycleScope.launch {
            while (isActive) {
                delay(60000)
                if (!pause) {
                    if (timeMinute >= 0) {
                        timeMinute--
                    }
                    if (timeMinute == 0) {
                        AudioPlay.stop()
                        postEvent(EventBus.AUDIO_DS, timeMinute)
                        break
                    }
                }
                postEvent(EventBus.AUDIO_DS, timeMinute)
                upAudioPlayNotification()
            }
        }
    }

    /**
     * 每隔1秒发送播放进�?     */
    private fun upPlayProgress() {
        upPlayProgressJob?.cancel()
        upPlayProgressJob = lifecycleScope.launch {
            while (isActive) {
                //更新buffer位置
                AudioPlay.playPositionChanged(exoPlayer.currentPosition.toInt())
                postEvent(EventBus.AUDIO_BUFFER_PROGRESS, exoPlayer.bufferedPosition.toInt())
                postEvent(EventBus.AUDIO_PROGRESS, AudioPlay.durChapterPos)
                postEvent(EventBus.AUDIO_SIZE, exoPlayer.duration.toInt())
                upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                delay(1000)
            }
        }
    }

    /**
     * 更新媒体状�?     */
    private fun upMediaSessionPlaybackState(state: Int) {
        mediaSessionCompat?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(MEDIA_SESSION_ACTIONS)
                .setState(state, exoPlayer.currentPosition, 1f)
                .setBufferedPosition(exoPlayer.bufferedPosition)
                .addCustomAction(
                    APP_ACTION_STOP,
                    getString(R.string.stop),
                    R.drawable.ic_stop_black_24dp
                )
                .addCustomAction(
                    APP_ACTION_TIMER,
                    getString(R.string.set_timer),
                    R.drawable.ic_time_add_24dp
                )
                .build()
        )
    }

    /**
     * 初始化MediaSession, 注册多媒体按�?     */
    @SuppressLint("UnspecifiedImmutableFlag")
    private fun initMediaSession() {
        mediaSessionCompat = MediaSessionCompat(this, "readAloud")
        mediaSessionCompat?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onSeekTo(pos: Long) {
                position = pos.toInt()
                exoPlayer.seekTo(pos)
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                return MediaButtonReceiver.handleIntent(this@AudioPlayService, mediaButtonEvent)
            }

            override fun onPlay() = resume()

            override fun onPause() = pause()

            override fun onCustomAction(action: String?, extras: Bundle?) {
                action ?: return

                when (action) {
                    APP_ACTION_STOP -> stopSelf()
                    APP_ACTION_TIMER -> addTimer()
                }
            }
        })
        mediaSessionCompat?.setMediaButtonReceiver(
            broadcastPendingIntent<MediaButtonReceiver>(Intent.ACTION_MEDIA_BUTTON)
        )
        mediaSessionCompat?.isActive = true
    }

    /**
     * 断开耳机监听
     */
    private fun initBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                    pause()
                }
            }
        }
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * 音频焦点变化
     */
    override fun onAudioFocusChange(focusChange: Int) {
        if (AppConfig.ignoreAudioFocus) {
            AppLog.put("忽略音频焦点处理(有声)")
            return
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnAudioFocusGain) {
                    AppLog.put("音频焦点获得,继续播放")
                    resume()
                } else {
                    AppLog.put("音频焦点获得")
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                AppLog.put("音频焦点丢失,暂停播放")
                pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                AppLog.put("音频焦点暂时丢失并会很快再次获得,暂停播放")
                if (!pause) {
                    needResumeOnAudioFocusGain = true
                    pause(false)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂丢失焦点，这种情况是被其他应用申请了短暂的焦点希望其他声音能压低音量（或者关闭声音）凸显这个声音（比如短信提示音），
                AppLog.put("音频焦点短暂丢失,不做处理")
            }
        }
    }

    private fun createNotification(): NotificationCompat.Builder {
        var nTitle: String = when {
            pause -> getString(R.string.audio_pause)
            timeMinute in 1..60 -> getString(
                R.string.playing_timer,
                timeMinute
            )

            else -> getString(R.string.audio_play_t)
        }
        nTitle += ": ${AudioPlay.book?.name}"
        var nSubtitle = AudioPlay.durChapter?.title
        if (nSubtitle.isNullOrEmpty()) {
            nSubtitle = getString(R.string.audio_play_s)
        }
        val builder = NotificationCompat
            .Builder(this@AudioPlayService, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText(getString(R.string.audio))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(nTitle)
            .setContentText(nSubtitle)
            .setContentIntent(
                activityPendingIntent<AudioPlayActivity>("activity")
            )
        builder.setLargeIcon(cover)
        if (pause) {
            builder.addAction(
                R.drawable.ic_play_24dp,
                getString(R.string.resume),
                servicePendingIntent<AudioPlayService>(IntentAction.resume)
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause_24dp,
                getString(R.string.pause),
                servicePendingIntent<AudioPlayService>(IntentAction.pause)
            )
        }
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.stop),
            servicePendingIntent<AudioPlayService>(IntentAction.stop)
        )
        builder.addAction(
            R.drawable.ic_time_add_24dp,
            getString(R.string.set_timer),
            servicePendingIntent<AudioPlayService>(IntentAction.addTimer)
        )
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSessionCompat?.sessionToken)
        )
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return builder
    }

    private fun upAudioPlayNotification() {
        upNotificationJob = execute {
            try {
                val notification = createNotification()
                notificationManager.notify(NotificationId.AudioPlayService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建音频播放通知出错,${e.localizedMessage}", e, true)
            }
        }
    }

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        execute {
            try {
                val notification = createNotification()
                startForeground(NotificationId.AudioPlayService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建音频播放通知出错,${e.localizedMessage}", e, true)
                //创建通知出错不结束服务就会崩�?服务必须绑定通知
                stopSelf()
            }
        }
    }

    /**
     * 请求音频焦点
     * @return 音频焦点
     */
    private fun requestFocus(): Boolean {
        if (AppConfig.ignoreAudioFocus) {
            return true
        }
        return MediaHelp.requestFocus(mFocusRequest)
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonFocus() {
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(this)
    }

}
