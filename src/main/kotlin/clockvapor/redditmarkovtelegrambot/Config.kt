package clockvapor.redditmarkovtelegrambot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

class Config {
    lateinit var telegramBotToken: String
    lateinit var redditClientId: String
    lateinit var redditClientSecret: String
    lateinit var redditAppId: String
    lateinit var redditAppVersion: String
    lateinit var redditUsername: String
    var redditFetchAmount: Int? = null
    var redditFetchIntervalMs: Long? = null

    companion object {
        fun read(path: String): Config =
            ObjectMapper(YAMLFactory()).readValue<Config>(File(path), Config::class.java)
    }
}
