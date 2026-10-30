package com.logus.app.ui.components

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File

/**
 * 폰 안에 있는 영상 파일 하나를 처음부터 끝까지 소리와 함께 재생한다(S05 찍은 영상 확인).
 * - 안드로이드 기본 MediaPlayer + TextureView 라서 라이브러리가 필요 없다. TextureView 는 둥근 모서리 자르기(clip)가 된다.
 * - 이 부품이 화면에 나타나면 바로 재생을 시작하고, 끝나면 onFinished 를 부른다(부른 쪽이 첫 장면으로 되돌린다).
 * - 앱을 내리면(ON_PAUSE) 멈추고 onFinished 를 부른다. 화면에서 사라지면 재생기를 정리한다.
 * - 영상 크기는 상자 크기에 맞춰 늘린다(S04 가 16:9 로 잘라 찍으므로 16:9 상자에 그대로 맞는다).
 */
@Composable
fun LocalVideoPlayer(file: File, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val finished by rememberUpdatedState(onFinished)
    val player = remember(file) { MediaPlayer() }

    // 화면에서 사라질 때 재생기 정리
    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }
    // 앱을 내리면 멈춘다(돌아왔을 때 소리가 갑자기 나지 않게)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                runCatching { if (player.isPlaying) player.pause() }
                finished()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        try {
                            player.setSurface(Surface(texture))
                            player.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                    .build(),
                            )
                            player.setDataSource(file.absolutePath)
                            player.setOnPreparedListener { it.start() }
                            player.setOnCompletionListener { finished() }
                            player.setOnErrorListener { _, what, extra ->
                                Log.w(TAG, "영상 재생 실패 ($what, $extra)")
                                finished()
                                true
                            }
                            player.prepareAsync()
                        } catch (e: Exception) {
                            Log.w(TAG, "영상 재생 준비 실패", e)
                            finished()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        runCatching { player.setSurface(null) }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

private const val TAG = "LocalVideoPlayer"
