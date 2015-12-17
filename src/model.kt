object html {
    @JvmStatic fun main(args: Array<String>) = gen(html::class)

    interface Document {
        val children: _0_n
    }

    @Parents(
            Document::class,
            Link::class
    )
    interface Link {
        val text: _1
    }

    interface Paragraph {
        val words: _0_n
    }

    interface Word {

    }
}
