package clockvapor.telegram.redditmarkov

import clockvapor.markov.MarkovChain
import com.fasterxml.jackson.databind.ObjectMapper
import net.dean.jraw.models.Comment
import java.io.File

// Needs to remain public for JSON writing
@Suppress("MemberVisibilityCanBePrivate")
class RedditMarkovChain(val ids: MutableSet<String> = mutableSetOf(),
                        data: MutableMap<String, MutableMap<String, Int>> = mutableMapOf())
    : MarkovChain(data) {

    fun add(comment: Comment): Boolean {
        val body = comment.body.trim()
        return if (body.isNotBlank() && ids.add(comment.uniqueId)) {
            add(body.split(Main.whitespaceRegex))
            true
        } else false
    }

    companion object {
        fun read(path: String): RedditMarkovChain =
            ObjectMapper().readValue<RedditMarkovChain>(File(path), RedditMarkovChain::class.java)
    }
}
