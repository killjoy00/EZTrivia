package com.rsm.eztrivia.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Fixed-size score card payload for Android share sheets. */
data class ScoreCardContent(
    val title: String,
    val subtitle: String,
    val headline: String,
    val outcomes: List<Boolean>,
    val footnote: String?,
    val tintArgb: Int,
)

object ScoreCardShare {
    private const val size = 1200

    fun share(
        context: Context,
        message: String,
        headline: String,
        card: ScoreCardContent,
    ) {
        runCatching {
            val bitmap = render(card)
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            val image = File(directory, "eztrivia-score-${System.currentTimeMillis()}.png")
            FileOutputStream(image).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                image,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_TITLE, headline)
                clipData = ClipData.newUri(context.contentResolver, headline, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share result"))
        }.getOrElse {
            shareTextOnly(context, message)
        }
    }

    private fun shareTextOnly(context: Context, message: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(send, "Share result"))
    }

    private fun render(card: ScoreCardContent): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                card.tintArgb,
                lighten(card.tintArgb, 0.42f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gradientPaint)

        drawCenteredText(canvas, "EZ TRIVIA", 150f, 44f, Typeface.BOLD, alpha = 195)
        drawCenteredText(canvas, card.title, 292f, 78f, Typeface.BOLD)
        drawCenteredText(canvas, card.subtitle, 378f, 43f, Typeface.NORMAL, alpha = 215)
        drawCenteredText(canvas, card.headline, 610f, 172f, Typeface.BOLD)
        drawOutcomeGrid(canvas, card.outcomes, 790f)
        card.footnote?.let { drawCenteredText(canvas, it, 1005f, 48f, Typeface.BOLD, alpha = 225) }

        return bitmap
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        baseline: Float,
        textSize: Float,
        style: Int,
        alpha: Int = 255,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = alpha
            this.textSize = textSize
            typeface = Typeface.create("sans-serif", style)
            textAlign = Paint.Align.CENTER
        }
        var fittedSize = textSize
        while (paint.measureText(text) > size - 120 && fittedSize > 28f) {
            fittedSize -= 4f
            paint.textSize = fittedSize
        }
        canvas.drawText(text, size / 2f, baseline, paint)
    }

    private fun drawOutcomeGrid(canvas: Canvas, outcomes: List<Boolean>, centerY: Float) {
        if (outcomes.isEmpty()) return
        val square = 72f
        val gap = 18f
        val totalWidth = outcomes.size * square + (outcomes.size - 1) * gap
        var left = (size - totalWidth) / 2f
        val top = centerY - square / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        outcomes.forEach { correct ->
            paint.color = if (correct) Color.rgb(94, 201, 123) else Color.argb(100, 255, 255, 255)
            canvas.drawRoundRect(RectF(left, top, left + square, top + square), 12f, 12f, paint)
            left += square + gap
        }
    }

    private fun lighten(color: Int, amount: Float): Int {
        val safe = amount.coerceIn(0f, 1f)
        fun channel(value: Int): Int = (value + (255 - value) * safe).toInt().coerceIn(0, 255)
        return Color.rgb(
            channel(Color.red(color)),
            channel(Color.green(color)),
            channel(Color.blue(color)),
        )
    }
}
