package com.ridthewann.ubahvo.data

import java.net.URI
import java.util.Locale

/** Friendly source labels for public social/video links routed through the existing yt-dlp engine. */
internal object MediaSourceDetector {
    fun displayName(rawUrl: String): String {
        val host = runCatching { URI(rawUrl).host?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return "sumber media"
        return when {
            host.isWithin("tiktok.com") || host.isWithin("tiktokv.com") -> "TikTok"
            host.isWithin("instagram.com") || host.isWithin("instagr.am") -> "Instagram"
            host.isWithin("facebook.com") || host.isWithin("fb.watch") || host.isWithin("fb.com") -> "Facebook"
            host.isWithin("x.com") || host.isWithin("twitter.com") || host.isWithin("t.co") -> "X / Twitter"
            host.isWithin("threads.net") || host.isWithin("threads.com") -> "Threads"
            host.isWithin("reddit.com") || host.isWithin("redditmedia.com") || host.isWithin("redd.it") -> "Reddit"
            host.isWithin("pinterest.com") || host.isWithin("pin.it") -> "Pinterest"
            host.isWithin("snapchat.com") || host.isWithin("snap.com") -> "Snapchat"
            host.isWithin("youtube.com") || host == "youtu.be" -> "YouTube"
            host.isWithin("vimeo.com") -> "Vimeo"
            host.isWithin("twitch.tv") -> "Twitch"
            host.isWithin("soundcloud.com") -> "SoundCloud"
            else -> "sumber media"
        }
    }

    private fun String.isWithin(domain: String): Boolean =
        this == domain || endsWith(".$domain")
}
