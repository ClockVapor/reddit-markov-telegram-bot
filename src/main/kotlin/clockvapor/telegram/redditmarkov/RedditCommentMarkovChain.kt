package clockvapor.telegram.redditmarkov

import clockvapor.markov.MarkovChain
import clockvapor.telegram.whitespaceRegex
import com.fasterxml.jackson.databind.ObjectMapper
import net.dean.jraw.models.Comment
import java.io.File

// Needs to remain public for JSON writing
@Suppress("MemberVisibilityCanBePrivate")
class RedditCommentMarkovChain(val ids: MutableSet<String> = mutableSetOf(),
                               data: MutableMap<String, MutableMap<String, Int>> = mutableMapOf())
    : MarkovChain(data) {

    fun add(comment: Comment): Boolean {
        val body = comment.body.trim()
        return if (body.isNotBlank() && ids.add(comment.uniqueId)) {
            add(body.split(whitespaceRegex))
            true
        } else false
    }

    companion object {
        fun read(path: String): RedditCommentMarkovChain =
            ObjectMapper().readValue<RedditCommentMarkovChain>(File(path), RedditCommentMarkovChain::class.java)
    }
}
