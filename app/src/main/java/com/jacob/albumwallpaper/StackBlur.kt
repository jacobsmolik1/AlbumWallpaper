package com.jacob.albumwallpaper

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.min

/**
 * StackBlur by Mario Klingemann — O(n) true Gaussian-like blur.
 * Run on a downsampled bitmap then upsample back for speed.
 */
object StackBlur {

    fun blur(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src
        val w   = src.width
        val h   = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)

        val div    = radius * 2 + 1
        val r1     = radius + 1
        val divsum = r1 * r1                       // weight sum for center pixel
        val dv     = IntArray(256 * divsum) { it / divsum }

        val rArr   = IntArray(w * h)
        val gArr   = IntArray(w * h)
        val bArr   = IntArray(w * h)
        val vmin   = IntArray(maxOf(w, h))
        val stack  = Array(div) { IntArray(3) }

        // ── Horizontal pass ────────────────────────────────────────────────────
        var yw = 0; var yi = 0
        for (y in 0 until h) {
            var rsum = 0; var gsum = 0; var bsum = 0
            var rinsum = 0; var ginsum = 0; var binsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0

            for (i in -radius..radius) {
                val p   = pix[yi + min(w - 1, maxOf(0, i))]
                val sir = stack[i + radius]
                sir[0]  = (p ushr 16) and 0xff
                sir[1]  = (p ushr 8)  and 0xff
                sir[2]  =  p          and 0xff
                val rbs = r1 - abs(i)
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else       { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
            }
            var sp = radius
            for (x in 0 until w) {
                rArr[yi] = dv[rsum]; gArr[yi] = dv[gsum]; bArr[yi] = dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum

                val ss  = (sp - radius + div) % div
                val sir = stack[ss]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

                if (y == 0) vmin[x] = min(x + radius + 1, w - 1)
                val p = pix[yw + vmin[x]]
                sir[0] = (p ushr 16) and 0xff; sir[1] = (p ushr 8) and 0xff; sir[2] = p and 0xff
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum   += rinsum;  gsum   += ginsum;  bsum   += binsum

                sp = (sp + 1) % div
                val sir2 = stack[sp]
                routsum += sir2[0]; goutsum += sir2[1]; boutsum += sir2[2]
                rinsum  -= sir2[0]; ginsum  -= sir2[1]; binsum  -= sir2[2]
                yi++
            }
            yw += w
        }

        // ── Vertical pass ──────────────────────────────────────────────────────
        for (x in 0 until w) {
            var rsum = 0; var gsum = 0; var bsum = 0
            var rinsum = 0; var ginsum = 0; var binsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0
            var yp = -radius * w

            for (i in -radius..radius) {
                val yi2 = maxOf(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = rArr[yi2]; sir[1] = gArr[yi2]; sir[2] = bArr[yi2]
                val rbs = r1 - abs(i)
                rsum += rArr[yi2] * rbs; gsum += gArr[yi2] * rbs; bsum += bArr[yi2] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else       { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
                if (i < h - 1) yp += w
            }
            yi = x; var sp = radius
            for (y in 0 until h) {
                pix[yi] = (0xff shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum

                val ss  = (sp - radius + div) % div
                val sir = stack[ss]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

                if (x == 0) vmin[y] = min(y + r1, h - 1) * w
                val p = x + vmin[y]
                sir[0] = rArr[p]; sir[1] = gArr[p]; sir[2] = bArr[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum   += rinsum;  gsum   += ginsum;  bsum   += binsum

                sp = (sp + 1) % div
                val sir2 = stack[sp]
                routsum += sir2[0]; goutsum += sir2[1]; boutsum += sir2[2]
                rinsum  -= sir2[0]; ginsum  -= sir2[1]; binsum  -= sir2[2]
                yi += w
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pix, 0, w, 0, 0, w, h)
        return result
    }
}
