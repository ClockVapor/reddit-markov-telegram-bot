# reddit-markov-telegram-bot

`reddit-markov-telegram-bot` is a Telegram bot that builds a Markov chain for requested subreddits, and it uses those
Markov chains to generate "new" user comments from those subreddits when prompted. The process is similar to your phone
keyboard's predictive text capabilities.

## Sample Usage

Start a conversation with the bot or add it to your group here: http://t.me/redditsimulatorbot

### /comment
To generate a comment from a subreddit, use the `/comment` command. The command expects a subreddit name following it
to know which subreddit you want to generate a comment from: `/comment pics`. You can also include an optional "seed"
word following the subreddit name to tell the bot which word to start with when it generates the comment:
`/comment pics blah`

When a subreddit is requested via this command, its newest comments are fetched and analyzed in bulk, and they are only
fetched a maximum of once per hour to prevent redundant traffic and analysis.

### /post
To generate a post from a subreddit (title and body), use the `/post` command. The command expects a subreddit name
following it to know which subreddit you want to generate a post from: `/post nostupidquestions`. The result will look
like this:

```
Post title
-----
Post body
```

When a subreddit is requested via this command, its top 100 hot posts are fetched and analyzed like so:

1. The post's title is added to a separate Markov chain that only tracks post titles.
2. If the post is a self post (only contains body text, and no direct link), its body text is added to the Markov chain
that only tracks body text.
3. If the post is a link post, the link URL is added to the body text Markov chain as a single entry.

Just like with the `/comment` command, posts are only fetched a maximum of once per hour.
