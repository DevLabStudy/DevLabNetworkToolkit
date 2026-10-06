package ovh.devlabstudy.devlabnetworktoolkit.util

import android.content.Context
import android.content.Intent
import ovh.devlabstudy.devlabnetworktoolkit.R
import java.net.InetAddress
import java.util.Locale

object ConverterUtils {

    data class IpConverted(val binary: String, val hex: String, val integerVal: Long)

    fun exportAndShareData(context: Context, title: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_chooser_title)))
    }

    fun convertIpAddress(ipStr: String): IpConverted? {
        return try {
            val inet = InetAddress.getByName(ipStr.trim())
            val bytes = inet.address
            if (bytes.size != 4) return null

            val ipLong = ((bytes[0].toLong() and 0xFF) shl 24) or
                    ((bytes[1].toLong() and 0xFF) shl 16) or
                    ((bytes[2].toLong() and 0xFF) shl 8) or
                    (bytes[3].toLong() and 0xFF)

            val binary = bytes.joinToString(".") {
                (it.toInt() and 0xFF).toString(2).padStart(8, '0')
            }
            val hex = bytes.joinToString(":") {
                (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
            }

            IpConverted(binary = binary, hex = hex, integerVal = ipLong)
        } catch (_: Exception) {
            null
        }
    }

    fun calculateDownloadTime(speedMbps: Double, sizeGb: Double): String {
        if ((speedMbps <= 0) || (sizeGb <= 0)) return "---"
        val sizeMb = sizeGb * 1024.0 * 8.0
        val secondsTotal = (sizeMb / speedMbps).toLong()

        val hours = secondsTotal / 3600
        val minutes = (secondsTotal % 3600) / 60
        val seconds = secondsTotal % 60

        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0) append("${minutes}m ")
            append("${seconds}s")
        }
    }

    fun formatDecimal(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }
}
