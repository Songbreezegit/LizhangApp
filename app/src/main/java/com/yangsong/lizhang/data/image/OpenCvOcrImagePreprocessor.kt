package com.yangsong.lizhang.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class OpenCvOcrImagePreprocessor(
    private val context: Context,
    private val maxDimension: Int = 2800,
) : OcrImagePreprocessor {
    override suspend fun preprocess(imageUri: String): OcrPreprocessResult = withContext(Dispatchers.Default) {
        val warnings = mutableListOf<String>()
        val uri = Uri.parse(imageUri)
        val bounds = readBounds(uri)
        var bitmap = decodeSampled(uri, bounds.first, bounds.second)
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        bitmap = correctExif(uri, bitmap)
        if (!OpenCVLoader.initLocal()) {
            warnings += "OpenCV 初始化失败，已安全使用方向修正后的图片"
            return@withContext saveFallback(imageUri, bitmap, originalWidth, originalHeight, warnings)
        }

        val source = Mat()
        val gray = Mat()
        val enhanced = Mat()
        var output = Mat()
        var perspectiveCorrected = false
        try {
            Utils.bitmapToMat(bitmap, source)
            val quadrilateral = detectPage(source)
            if (quadrilateral != null) {
                output = warp(source, quadrilateral)
                perspectiveCorrected = true
            } else {
                output = source.clone()
                warnings += "未检测到可靠页面边缘，已保留完整画面"
            }
            Imgproc.cvtColor(output, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.bilateralFilter(gray, enhanced, 5, 35.0, 35.0)
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(enhanced, enhanced)
            clahe.collectGarbage()
            val shadow = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(31.0, 31.0))
            Imgproc.morphologyEx(enhanced, shadow, Imgproc.MORPH_CLOSE, kernel)
            Core.divide(enhanced, shadow, enhanced, 255.0)
            kernel.release()
            shadow.release()
            val resultBitmap = Bitmap.createBitmap(enhanced.cols(), enhanced.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(enhanced, resultBitmap)
            saveProcessed(
                imageUri,
                resultBitmap,
                originalWidth,
                originalHeight,
                perspectiveCorrected,
                usedFallback = false,
                warnings = warnings,
            ).also { resultBitmap.recycle() }
        } catch (_: Throwable) {
            warnings += "图像增强失败，已安全使用方向修正后的图片"
            saveFallback(imageUri, bitmap, originalWidth, originalHeight, warnings)
        } finally {
            source.release()
            gray.release()
            enhanced.release()
            output.release()
            bitmap.recycle()
        }
    }

    override fun clean(result: OcrPreprocessResult) {
        val uri = Uri.parse(result.processedUri)
        if (uri.scheme == "file") File(requireNotNull(uri.path)).takeIf { it.parentFile?.name == "ocr_preprocessed" }?.delete()
    }

    private fun readBounds(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        return options.outWidth.coerceAtLeast(1) to options.outHeight.coerceAtLeast(1)
    }

    private fun decodeSampled(uri: Uri, width: Int, height: Int): Bitmap {
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return requireNotNull(context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        }) { "无法读取图片" }
    }

    private fun correctExif(uri: Uri, source: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri).use {
                ExifInterface(requireNotNull(it)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
        if (matrix.isIdentity) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also { source.recycle() }
    }

    private fun detectPage(source: Mat): Array<Point>? {
        val gray = Mat()
        val edges = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        return try {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 60.0, 180.0)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val imageArea = source.width().toDouble() * source.height()
            contours.asSequence()
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(10)
                .mapNotNull { contour ->
                    val curve = MatOfPoint2f(*contour.toArray())
                    val approximation = MatOfPoint2f()
                    Imgproc.approxPolyDP(curve, approximation, 0.02 * Imgproc.arcLength(curve, true), true)
                    val points = approximation.toArray()
                    curve.release()
                    approximation.release()
                    points.takeIf {
                        it.size == 4 &&
                            Imgproc.isContourConvex(MatOfPoint(*it)) &&
                            Imgproc.contourArea(MatOfPoint(*it)) >= imageArea * 0.45
                    }
                }.firstOrNull()?.let(::orderCorners)
        } finally {
            gray.release()
            edges.release()
            hierarchy.release()
            contours.forEach(Mat::release)
        }
    }

    private fun orderCorners(points: Array<Point>): Array<Point> {
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.maxBy { it.x - it.y }
        val bottomLeft = points.minBy { it.x - it.y }
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun warp(source: Mat, corners: Array<Point>): Mat {
        val width = maxOf(distance(corners[0], corners[1]), distance(corners[2], corners[3])).toInt()
        val height = maxOf(distance(corners[0], corners[3]), distance(corners[1], corners[2])).toInt()
        val destination = MatOfPoint2f(
            Point(0.0, 0.0), Point(width - 1.0, 0.0),
            Point(width - 1.0, height - 1.0), Point(0.0, height - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(MatOfPoint2f(*corners), destination)
        return Mat(height, width, CvType.CV_8UC4).also {
            Imgproc.warpPerspective(source, it, transform, Size(width.toDouble(), height.toDouble()))
            transform.release()
            destination.release()
        }
    }

    private fun distance(a: Point, b: Point) = kotlin.math.hypot(a.x - b.x, a.y - b.y)

    private fun saveFallback(
        originalUri: String,
        bitmap: Bitmap,
        originalWidth: Int,
        originalHeight: Int,
        warnings: List<String>,
    ) = saveProcessed(originalUri, bitmap, originalWidth, originalHeight, false, true, warnings)

    private fun saveProcessed(
        originalUri: String,
        bitmap: Bitmap,
        originalWidth: Int,
        originalHeight: Int,
        perspectiveCorrected: Boolean,
        usedFallback: Boolean,
        warnings: List<String>,
    ): OcrPreprocessResult {
        val directory = File(context.cacheDir, "ocr_preprocessed").apply { mkdirs() }
        val file = File.createTempFile("ocr_", ".jpg", directory)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return OcrPreprocessResult(
            originalUri = originalUri,
            processedUri = Uri.fromFile(file).toString(),
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            processedWidth = bitmap.width,
            processedHeight = bitmap.height,
            perspectiveCorrected = perspectiveCorrected,
            usedFallback = usedFallback,
            warnings = warnings,
        )
    }
}
