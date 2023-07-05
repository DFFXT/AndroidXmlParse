package struct

import org.w3c.dom.Node
import org.w3c.dom.NodeList
import struct.writer.*
import struct.writer.Attribute
import java.io.InputStream
import java.nio.ByteBuffer
import javax.xml.parsers.DocumentBuilderFactory

/**
 * xml 转 axml
 * 在解析时就应该创建axml结构
 */
class XmlCompiler {
    private val chunkFile = ChunkFileWriter()
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()

    // key 是字符串，value是字节偏移
    private val stringPool = LinkedHashMap<String, Pair<Int, Int>>()
    private var poolByteSize = 0
    private var filterTextNode = true
    private lateinit var buffer: ByteBuffer
    fun compile(stream: InputStream, buffer: ByteBuffer) {
        this.buffer = buffer
        val doc = builder.parse(stream)
        // step 1：创建字符常量池
        // 记录所有的node name、attribute name、attribute value、namespace
        val len = doc.childNodes.length
        compile(doc.childNodes, buffer, true)
        compile(doc.childNodes, buffer, false)
        chunkFile.write(buffer)
        buffer.limit(chunkFile.chunkSize)
    }

    private fun compile(nodes: NodeList, buffer: ByteBuffer, findStr: Boolean) {
        val len = nodes.length
        for (i in 0 until len) {
            val node = nodes.item(i)
            if (filterTextNode && node.nodeType == Node.TEXT_NODE) {
                // 过滤text节点
                continue
            }
            if (node.nodeType == Node.COMMENT_NODE) {
                continue
            }
            addStartTag(node, findStr)
            compile(node.childNodes, buffer, findStr)
            /*if (node.hasAttributes()) {
                val attrs = node.attributes
                for (attrIndex in 0 until attrs.length) {
                    val attr = attrs.item(attrIndex)
                    // 这里是命名空间
                    if (attr.nodeName.startsWith("xmlns:")) {
                        addNamespace(attr, findStr)
                    } else {
                        addAttribute(attr, findStr)
                    }
                }
            }*/
            addEndTag(node, findStr)
        }
    }

    private fun addString(string: String): Int {
        val preByteSize = poolByteSize
        if (!stringPool.contains(string)) {
            stringPool[string] = Pair(stringPool.size, poolByteSize)
            chunkFile.chunkString.stringOffsets.add(poolByteSize)
            chunkFile.chunkString.stringPools[poolByteSize] = ChunkStringWriter.StringPool( string)
            poolByteSize += string.toByteArray().size + ChunkStringWriter.StringPool.extra_size_utf8
            println("add:->"+string)
        }

        return chunkFile.chunkString.stringOffsets.indexOf(preByteSize)
    }

    private fun getStringIndex(str: String): Int {
        println(str)
        return stringPool[str]!!.first
    }

    private fun addNamespace(node: Node ,findStr: Boolean) {
        val prefix = node.nodeName.substring("xmlns:".length)
        val uri = node.nodeValue
        if (findStr) {
            addString(uri)
            addString(prefix)
        } else {
            val index = getStringIndex(uri)
            chunkFile.chunkStartNamespace[index] = ChunkNamespaceWriter(0, ChunkNamespaceWriter.TYPE_START).apply {
                this.comment = -1
                this.uri = index
                this.lineNumber = 0
                this.prefix = getStringIndex(prefix)
            }
            chunkFile.chunkEndNamespace[index] = ChunkNamespaceWriter(0, ChunkNamespaceWriter.TYPE_END).apply {
                this.comment = -1
                this.uri = index
                this.lineNumber = 0
                this.prefix = getStringIndex(prefix)
            }
        }
        // 这里将命名空间提升到顶级， todo 确定命名空间实际位置
    }

    private fun addAttribute(attr: Node, findStr: Boolean) {
        if (findStr) {
            // 这里需要判断是否是引用类型

            if (attr.nodeName.contains(":")) {
                val attrSplit = attr.nodeName.split(":")
                addString(attrSplit[1])
                addString(attrSplit[0])
                //addString(attr.nodeValue)
            } else {
                addString(attr.nodeName)
            }
        } else {
            (chunkFile.chunkTags.last as ChunkStartTagWriter).attributes.add(Attribute().apply {
                if (attr.nodeName.contains(":")) {
                    val attrSplit = attr.nodeName.split(":")
                    this.name = getStringIndex(attrSplit[1])
                    val prefixIndex = getStringIndex(attrSplit[0])
                    var uri = 0
                    for (ns in chunkFile.chunkStartNamespace) {
                        if (ns.value.prefix == prefixIndex) {
                            uri = ns.value.uri
                            break
                        }
                    }
                    this.namespaceUri = uri
                } else {
                    this.name = getStringIndex(attr.nodeName)
                    this.namespaceUri = -1
                }
                // 从常量池获取，因为目前所有值都在这里
                this.value = -1
                this.resValue = Attribute.ResValue().apply {
                    this.data = value
                    this.res0 = 0
                    this.size = 8
                }
            })
        }

    }

    private fun addStartTag(node: Node, findStr: Boolean) {
        if (findStr) {
            addString(node.nodeName)
            if (node.hasAttributes()) {
                val attrs = ArrayList<Node>()
                for (i in 0 until node.attributes.length) {
                    val attr = node.attributes.item(i)
                    // 这里是命名空间
                    if (attr.nodeName.startsWith("xmlns:")) {
                        addNamespace(attr, findStr)
                    } else {
                        attrs.add(attr)
                        // addAttribute(attr, findStr)
                    }

                }
                attrs.forEach {
                    addAttribute(it, findStr)
                }
            }
        } else {
            val tag = ChunkStartTagWriter(0)
            chunkFile.chunkTags.add(tag)
            tag.apply {
                this.name = getStringIndex(node.nodeName)
                // tag 暂时不算命名空间
                this.namespaceUri = -1
                this.comment = -1
                this.idIndex = 0
                this.classIndex = 0
                this.styleIndex = 0
                if (node.hasAttributes()) {
                    this.attrCount = node.attributes.length.toShort()

                    val attrs = ArrayList<Node>()
                    for (i in 0 until node.attributes.length) {
                        val attr = node.attributes.item(i)
                        // 这里是命名空间
                        if (attr.nodeName.startsWith("xmlns:")) {
                            addNamespace(attr, findStr)
                        } else {
                            attrs.add(attr)
                            // addAttribute(attr, findStr)
                        }

                    }
                    attrs.forEach {
                        addAttribute(it, findStr)
                    }
                    /*for (i in 0 until node.attributes.length) {
                        val attr = node.attributes.item(i)
                        // 这里是命名空间
                        if (attr.nodeName.startsWith("xmlns:")) {
                            addNamespace(attr, findStr)
                        } else {
                            addAttribute(attr, findStr)
                        }

                    }*/
                }
            }
        }
    }

    private fun addEndTag(node: Node, findStr: Boolean) {
        if (findStr) {
            addString(node.nodeName)
        } else {
            chunkFile.chunkTags.add(ChunkEndTagWriter(0).apply {
                this.name = getStringIndex(node.nodeName)
                // tag 暂时不算命名空间
                this.namespaceUri = -1
                this.comment = -1
            })
        }
    }


}