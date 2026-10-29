package com.gfontcreator.app.font

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * 生成 WRobot / 奎享写字机器人可用的 `.gfont` 单线字库。
 *
 * 与 TTF（填充轮廓）不同，写字机器人需要的是**笔尖行走路径**（中心线）：
 * 每个字由若干笔画折线组成。文件结构（已用真实字库逆向并与 WRobot 源码核对）：
 *
 * ```text
 * 明文头（版本 3 + 元数据，WRobot 会跳过）
 * 内嵌标准 ZIP（deflate）：条目名 = Unicode 码点十进制
 *   记录 = u16 BE 码点
 *        + u32 BE 坐标浮点数个数 (2 × 点数)
 *        + f32 BE × n (x0,y0,x1,y1,...，y 轴向下)
 *        + u32 BE 点数
 *        + u8 × 点数（0 = 抬笔移动到该点，1 = 落笔绘制到该点）
 * ```
 *
 * 注意：`.gfont` **不是** WOFF/Google Fonts 封装，之前的 WOFF 导出无法被 WRobot 读取。
 */
object GFontBuilder {

    /** WRobot 加载 `.gfont` 时使用的 em 单位 */
    const val EM_UNITS = 500

    /** 版式内容框占 em 的比例，与 TTF 管线（BitmapVectorizer 8% 边距）保持一致 */
    const val CONTENT_RATIO = 0.84f

    private const val VERSION = 3
    private const val IDENTIFIER = "kvenjoy"
    private const val DOS_DATE = 0x5821 // 2024-01-01
    private const val DOS_TIME = 0

    data class Point(val x: Float, val y: Float)

    /** 一个字：码点 + 若干笔画（每笔为 ≥2 个点的折线，坐标 y 向下） */
    data class Glyph(val codepoint: Int, val strokes: List<List<Point>>)

    data class Metadata(
        val name: String,
        val vendor: String = "GFontCreator",
        val description: String = "手写单线字库",
        val emUnits: Int = EM_UNITS,
    )

    /**
     * @param glyphs 目标字符集；会自动补充空格字形（WRobot 排版依赖它推进光标）
     */
    fun build(glyphs: List<Glyph>, meta: Metadata): ByteArray {
        val merged = LinkedHashMap<Int, List<List<Point>>>()
        for (g in glyphs) {
            if (g.codepoint !in 1..0xFFFF) continue
            val strokes = sanitize(g.strokes)
            if (strokes.isNotEmpty()) merged[g.codepoint] = strokes
        }
        // 空格：无笔画但必须存在，否则 WRobot 排版遇到空格不会推进
        merged.putIfAbsent(0x20, emptyList())

        val entries = merged.entries
            .sortedBy { it.key }
            .map { (cp, strokes) -> ZipEntry(cp.toString(), glyphRecord(cp, strokes)) }

        val out = ByteArrayOutputStream()
        writeHeader(out, meta, entries.size, merged.values)
        out.write(zip(entries))
        return out.toByteArray()
    }

    /** 单字记录（大端） */
    fun glyphRecord(codepoint: Int, strokes: List<List<Point>>): ByteArray {
        val flat = ArrayList<Point>()
        val flags = ArrayList<Byte>()
        for (stroke in strokes) {
            if (stroke.size < 2) continue
            stroke.forEachIndexed { i, p ->
                flat.add(p)
                flags.add(if (i == 0) 0 else 1)
            }
        }
        val w = BeWriter()
        w.u16(codepoint)
        w.u32(flat.size * 2L)
        for (p in flat) {
            w.f32(p.x)
            w.f32(p.y)
        }
        w.u32(flat.size.toLong())
        for (f in flags) w.u8(f.toInt())
        return w.toByteArray()
    }

    // ------------------------------------------------------------------
    // 头（与真实 v3 字库同构；WRobot 只依赖之后的 ZIP）
    // ------------------------------------------------------------------

    private fun writeHeader(
        out: ByteArrayOutputStream,
        meta: Metadata,
        glyphCount: Int,
        allStrokes: Collection<List<List<Point>>>,
    ) {
        val w = BeWriter()
        w.u32(VERSION.toLong())
        writeUtf8(w, IDENTIFIER)
        w.u32(4)
        writeUtf8(w, meta.name)
        writeUtf8(w, meta.vendor)
        writeUtf8(w, meta.description)
        w.u32(meta.emUnits.toLong())
        w.u32(glyphCount.toLong())
        w.u32(0)
        var totalPoints = 0L
        var totalStrokes = 0L
        for (strokes in allStrokes) {
            for (s in strokes) {
                if (s.size < 2) continue
                totalPoints += s.size
                totalStrokes++
            }
        }
        w.u32(totalPoints)
        w.u32(totalStrokes)
        out.write(w.toByteArray())
    }

    private fun writeUtf8(w: BeWriter, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        w.u16(bytes.size)
        w.bytes(bytes)
    }

    // ------------------------------------------------------------------
    // ZIP（method 8 / deflate；WRobot 只接受 method 8）
    // ------------------------------------------------------------------

    private data class ZipEntry(val name: String, val data: ByteArray)

    private fun zip(entries: List<ZipEntry>): ByteArray {
        val local = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        val crc = CRC32()

        for (e in entries) {
            val name = e.name.toByteArray(Charsets.US_ASCII)
            crc.reset()
            crc.update(e.data)
            val crcValue = crc.value
            val compressed = deflateRaw(e.data)
            val localOffset = local.size()

            val lw = LeWriter()
            lw.u32(0x04034B50L)
            lw.u16(20)
            lw.u16(0)                 // flags
            lw.u16(8)                 // method: deflate
            lw.u16(DOS_TIME)
            lw.u16(DOS_DATE)
            lw.u32(crcValue)
            lw.u32(compressed.size.toLong())
            lw.u32(e.data.size.toLong())
            lw.u16(name.size)
            lw.u16(0)
            lw.bytes(name)
            local.write(lw.toByteArray())
            local.write(compressed)

            val cw = LeWriter()
            cw.u32(0x02014B50L)
            cw.u16(20)                // version made by
            cw.u16(20)                // version needed
            cw.u16(0)                 // flags
            cw.u16(8)                 // method
            cw.u16(DOS_TIME)
            cw.u16(DOS_DATE)
            cw.u32(crcValue)
            cw.u32(compressed.size.toLong())
            cw.u32(e.data.size.toLong())
            cw.u16(name.size)
            cw.u16(0)                 // extra
            cw.u16(0)                 // comment
            cw.u16(0)                 // disk
            cw.u16(0)                 // internal attrs
            cw.u32(0)                 // external attrs
            cw.u32(localOffset.toLong())
            cw.bytes(name)
            central.write(cw.toByteArray())
        }

        val localBytes = local.toByteArray()
        val centralBytes = central.toByteArray()

        val eocd = LeWriter()
        eocd.u32(0x06054B50L)
        eocd.u16(0)
        eocd.u16(0)
        eocd.u16(entries.size)
        eocd.u16(entries.size)
        eocd.u32(centralBytes.size.toLong())
        eocd.u32(localBytes.size.toLong())
        eocd.u16(0)

        val out = ByteArrayOutputStream(localBytes.size + centralBytes.size + 22)
        out.write(localBytes)
        out.write(centralBytes)
        out.write(eocd.toByteArray())
        return out.toByteArray()
    }

    private fun deflateRaw(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(data.size / 2 + 64)
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    // ------------------------------------------------------------------
    // 数据清洗
    // ------------------------------------------------------------------

    private fun sanitize(strokes: List<List<Point>>): List<List<Point>> {
        val out = ArrayList<List<Point>>()
        for (stroke in strokes) {
            val pts = ArrayList<Point>(stroke.size)
            for (p in stroke) {
                if (!p.x.isFinite() || !p.y.isFinite()) continue
                val q = Point(round2(p.x), round2(p.y))
                val prev = pts.lastOrNull()
                if (prev != null && kotlin.math.abs(prev.x - q.x) < 0.05f &&
                    kotlin.math.abs(prev.y - q.y) < 0.05f
                ) continue
                pts.add(q)
            }
            when {
                pts.size >= 2 -> out.add(pts)
                pts.size == 1 -> out.add(listOf(pts[0], Point(pts[0].x + 2f, pts[0].y)))
            }
        }
        return out
    }

    private fun round2(v: Float): Float = kotlin.math.round(v * 100f) / 100f

    // ------------------------------------------------------------------
    // 写入器
    // ------------------------------------------------------------------

    private class BeWriter {
        private val out = ByteArrayOutputStream(64)
        fun u8(v: Int) = out.write(v and 0xFF)
        fun u16(v: Int) {
            out.write((v shr 8) and 0xFF); out.write(v and 0xFF)
        }
        fun u32(v: Long) {
            out.write(((v shr 24) and 0xFF).toInt())
            out.write(((v shr 16) and 0xFF).toInt())
            out.write(((v shr 8) and 0xFF).toInt())
            out.write((v and 0xFF).toInt())
        }
        fun f32(v: Float) = u32(v.toRawBits().toLong() and 0xFFFFFFFFL)
        fun bytes(b: ByteArray) = out.write(b)
        fun toByteArray(): ByteArray = out.toByteArray()
    }

    private class LeWriter {
        private val out = ByteArrayOutputStream(64)
        fun u16(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }
        fun u32(v: Long) {
            out.write((v and 0xFF).toInt())
            out.write(((v shr 8) and 0xFF).toInt())
            out.write(((v shr 16) and 0xFF).toInt())
            out.write(((v shr 24) and 0xFF).toInt())
        }
        fun bytes(b: ByteArray) = out.write(b)
        fun toByteArray(): ByteArray = out.toByteArray()
    }
}
