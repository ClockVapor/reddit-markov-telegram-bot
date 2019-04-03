package clockvapor.telegram.redditmarkov

import clockvapor.markov.MarkovChain
import clockvapor.telegram.whitespaceRegex
import com.fasterxml.jackson.databind.ObjectMapper
import net.dean.jraw.models.Submission
import java.io.File

// Needs to remain public for JSON writing
@Suppress("MemberVisibilityCanBePrivate")
class RedditPostMarkovChain(val ids: MutableSet<String> = mutableSetOf(),
                            bodyData: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
                            val titleData: MutableMap<String, MutableMap<String, Int>> = mutableMapOf())
    : MarkovChain(bodyData) {

    fun add(post: Submission): Boolean {
        if (ids.add(post.uniqueId)) {
            post.title?.takeIf { it.isNotBlank() }?.let { title ->
                add(title.split(whitespaceRegex), this.titleData)
            }
            if (post.isSelfPost) {
                post.selfText?.takeIf { it.isNotBlank() }?.let { selfText ->
                    add(selfText.split(whitespaceRegex))
                    return true
                }
            } else {
                post.url?.takeIf { it.isNotBlank() }?.let { url ->
                    add(listOf(url))
                    return true
                }
            }
        }
        return false
    }

    fun generateTitle(): List<String> =
        generate(titleData)

    companion object {
        fun read(path: String): RedditPostMarkovChain =
            ObjectMapper().readValue<RedditPostMarkovChain>(File(path), RedditPostMarkovChain::class.java)
    }
}
