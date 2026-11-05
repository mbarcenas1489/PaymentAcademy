package com.example.paymentacademy.receipt

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.paymentacademy.R
import com.example.paymentacademy.data.model.PaymentSummary
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PaymentReceiptData(
  val memberName: String,
  val amount: Double,
  val paidOn: String,
  val paymentType: String,
  val paymentMethod: String,
  val validFrom: String,
  val validUntil: String,
  val reference: String?,
  val receiptNumber: String? = null,
  val memberDocument: String? = null,
)

object PaymentReceiptSharer {
  private const val WIDTH = 1080
  private const val HEIGHT = 1880
  private val locale = Locale.forLanguageTag("es-CO")
  private val longDate = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", locale)
  private val generatedAtFormat = DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a", locale)

  fun share(context: Context, payment: PaymentSummary): Boolean = share(
    context,
    PaymentReceiptData(
      memberName = payment.memberName,
      amount = payment.amount,
      paidOn = payment.paidOn,
      paymentType = paymentTypeLabel(context, payment.paymentType),
      paymentMethod = paymentMethodLabel(context, payment.paymentMethod),
      validFrom = payment.validFrom ?: payment.paidOn,
      validUntil = payment.validUntil ?: payment.paidOn,
      reference = payment.reference,
      receiptNumber = payment.id,
      memberDocument = payment.memberDocument,
    ),
  )

  fun share(context: Context, data: PaymentReceiptData): Boolean {
    val receiptUri = createReceiptUri(context, data)
    val message = "Comprobante de pago de ${data.memberName}."
    val baseIntent = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, receiptUri)
      putExtra(Intent.EXTRA_TEXT, message)
      clipData = ClipData.newUri(context.contentResolver, "Comprobante de pago", receiptUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    for (packageName in listOf("com.whatsapp", "com.whatsapp.w4b")) {
      try {
        context.startActivity(Intent(baseIntent).setPackage(packageName))
        return true
      } catch (_: ActivityNotFoundException) {
        // Prueba la siguiente edición de WhatsApp o el selector del sistema.
      }
    }

    return try {
      context.startActivity(Intent.createChooser(baseIntent, "Enviar comprobante"))
      true
    } catch (_: ActivityNotFoundException) {
      false
    }
  }

  private fun createReceiptUri(context: Context, data: PaymentReceiptData): Uri {
    val directory = File(context.cacheDir, "shared_receipts").apply { mkdirs() }
    directory.listFiles()?.forEach { oldFile -> oldFile.delete() }
    val file = File(directory, "comprobante_pago_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { output ->
      check(drawReceipt(context, data).compress(Bitmap.CompressFormat.PNG, 100, output)) {
        "No fue posible crear la imagen del comprobante."
      }
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  private fun drawReceipt(context: Context, data: PaymentReceiptData): Bitmap {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    canvas.drawColor(Color.rgb(255, 248, 242))
    paint.color = Color.rgb(139, 61, 31)
    canvas.drawRect(0f, 0f, WIDTH.toFloat(), 260f, paint)

    paint.color = Color.WHITE
    canvas.drawCircle(110f, 104f, 54f, paint)
    paint.color = Color.rgb(139, 61, 31)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 12f
    canvas.drawPath(
      Path().apply {
        moveTo(78f, 105f)
        lineTo(101f, 129f)
        lineTo(145f, 79f)
      },
      paint,
    )
    paint.style = Paint.Style.FILL

    drawText(canvas, paint, context.getString(com.example.paymentacademy.R.string.app_name), 190f, 100f, 40f, Color.WHITE, true)
    drawText(canvas, paint, "COMPROBANTE DE PAGO", 190f, 157f, 48f, Color.WHITE, true)
    drawText(canvas, paint, "Pago recibido correctamente", 190f, 207f, 28f, Color.rgb(255, 224, 211))
    data.receiptNumber?.takeIf { it.isNotBlank() }?.let { number ->
      drawText(
        canvas,
        paint,
        "N.º ${number.take(8).uppercase()}",
        970f,
        235f,
        24f,
        Color.rgb(255, 224, 211),
        true,
        Paint.Align.RIGHT,
      )
    }

    paint.color = Color.WHITE
    canvas.drawRoundRect(RectF(56f, 304f, 1024f, 1770f), 32f, 32f, paint)

    drawText(canvas, paint, "TOTAL PAGADO", 540f, 395f, 27f, Color.rgb(118, 95, 82), true, Paint.Align.CENTER)
    drawText(
      canvas,
      paint,
      NumberFormat.getCurrencyInstance(locale).format(data.amount),
      540f,
      480f,
      67f,
      Color.rgb(37, 26, 20),
      true,
      Paint.Align.CENTER,
    )
    paint.color = Color.rgb(221, 243, 228)
    canvas.drawRoundRect(RectF(390f, 515f, 690f, 575f), 30f, 30f, paint)
    drawText(canvas, paint, "PAGO CONFIRMADO", 540f, 555f, 23f, Color.rgb(23, 107, 58), true, Paint.Align.CENTER)

    paint.color = Color.rgb(232, 220, 212)
    canvas.drawRect(105f, 625f, 975f, 627f, paint)

    var y = 700f
    y = drawRow(canvas, paint, "RECIBIDO DE", data.memberName, y)
    data.memberDocument?.takeIf { it.isNotBlank() }?.let { document ->
      y = drawRow(canvas, paint, "DOCUMENTO", document, y)
    }
    y = drawRow(canvas, paint, "CONCEPTO", data.paymentType, y)
    y = drawRow(canvas, paint, "MÉTODO", data.paymentMethod, y)
    y = drawRow(canvas, paint, "FECHA DE PAGO", formatDate(data.paidOn), y)
    y = drawRow(
      canvas,
      paint,
      "VIGENCIA",
      "${formatDate(data.validFrom)} – ${formatDate(data.validUntil)}",
      y,
    )
    data.reference?.takeIf { it.isNotBlank() }?.let { reference ->
      drawRow(canvas, paint, "REFERENCIA", reference, y)
    }

    drawText(
      canvas,
      paint,
      "Gracias por tu pago",
      540f,
      1820f,
      27f,
      Color.rgb(118, 95, 82),
      false,
      Paint.Align.CENTER,
    )
    drawText(
      canvas,
      paint,
      "Generado: ${LocalDateTime.now().format(generatedAtFormat)}",
      540f,
      1855f,
      20f,
      Color.rgb(118, 95, 82),
      false,
      Paint.Align.CENTER,
    )
    return bitmap
  }

  private fun drawRow(canvas: Canvas, paint: Paint, label: String, value: String, y: Float): Float {
    drawText(canvas, paint, label, 115f, y, 23f, Color.rgb(118, 95, 82), true)
    paint.textSize = 33f
    paint.typeface = android.graphics.Typeface.DEFAULT
    val lines = wrapText(value, paint, 850f, maxLines = 2)
    lines.forEachIndexed { index, line ->
      drawText(
        canvas,
        paint,
        line,
        115f,
        y + 48f + (index * 40f),
        33f,
        Color.rgb(37, 26, 20),
        false,
      )
    }
    return y + 112f + ((lines.size - 1).coerceAtLeast(0) * 40f)
  }

  private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return listOf("")
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
      val candidate = if (current.isEmpty()) word else "$current $word"
      if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
        current = candidate
      } else {
        lines += current
        current = word
        if (lines.size == maxLines - 1) break
      }
    }
    if (lines.size < maxLines && current.isNotEmpty()) lines += current
    if (lines.size == maxLines && words.joinToString(" ") != lines.joinToString(" ")) {
      var last = lines.last()
      while (last.isNotEmpty() && paint.measureText("$last…") > maxWidth) last = last.dropLast(1)
      lines[lines.lastIndex] = "$last…"
    }
    return lines
  }

  private fun drawText(
    canvas: Canvas,
    paint: Paint,
    text: String,
    x: Float,
    y: Float,
    size: Float,
    color: Int,
    bold: Boolean = false,
    align: Paint.Align = Paint.Align.LEFT,
  ) {
    paint.color = color
    paint.textSize = size
    paint.textAlign = align
    paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    canvas.drawText(text, x, y, paint)
  }

  private fun formatDate(value: String): String =
    runCatching { LocalDate.parse(value).format(longDate) }.getOrDefault(value)

  private fun paymentTypeLabel(context: Context, type: String): String = context.getString(
    when (type) {
      "monthly_fee" -> R.string.type_monthly_fee
      "enrollment" -> R.string.type_enrollment
      "class_package" -> R.string.type_class_package
      "personal_training" -> R.string.type_personal_training
      else -> R.string.type_other
    },
  )

  private fun paymentMethodLabel(context: Context, method: String): String = context.getString(
    when (method) {
      "cash" -> R.string.method_cash
      "card" -> R.string.method_card
      "transfer" -> R.string.method_transfer
      "online" -> R.string.method_online
      else -> R.string.method_other
    },
  )
}
