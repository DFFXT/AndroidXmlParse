import struct.ChunkFile
import struct.XmlCompiler
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AXMLReader {

    @JvmStatic
    fun main(vararg args: String) {

        val s = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<androidx.constraintlayout.widget.ConstraintLayout\n" +
                "\txmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "\tandroid:layout_width=\"-1\"\n" +
                "\tandroid:layout_height=\"-1\">\n" +
                "</androidx.constraintlayout.widget.ConstraintLayout>"
        val compiler = XmlCompiler()
        val outBuffer = ByteBuffer.allocate(10000).order(ByteOrder.LITTLE_ENDIAN)
        compiler.compile(ByteArrayInputStream(s.toByteArray()), outBuffer)

        val file = File("view_debug_test.xml")
        // val file = File("AndroidManifest.xml")
        val data = file.readBytes()
        val byteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val chunkFile = ChunkFile()
        outBuffer.position(0)
        chunkFile.read(outBuffer)


        val stdFile = ChunkFile()
        stdFile.read(byteBuffer)

        val result = chunkFile.toString()
        val arr = ByteArray(outBuffer.limit())
        outBuffer.position(0)
        outBuffer.get(arr, 0, arr.size)
        File("test.xml").writeBytes(arr)
        println(result)
    }
}