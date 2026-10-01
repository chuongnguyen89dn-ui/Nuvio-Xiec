package com.nuvio.app.features.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YouTubeChannelResolverTest {
    @Test
    fun preservesAuthoritativeHomeSectionOrder() {
        val snapshot = YouTubeChannelResolver.resolve(
            """{
              "channel":{"channelId":"UC1","handle":"@demo","name":"Demo"},
              "home":{"sections":[
                {"id":"featured","title":"Featured","type":"featured","items":["v2"]},
                {"id":"latest","title":"Videos","type":"videos","items":["v1","v2"]}
              ]},
              "videos":[
                {"videoId":"v1","title":"One","url":"https://www.youtube.com/watch?v=v1","duration":12},
                {"videoId":"v2","title":"Two","url":"https://www.youtube.com/watch?v=v2","duration":34}
              ]
            }"""
        )
        assertTrue(snapshot.hasAuthoritativeHomeLayout)
        assertEquals(listOf("featured", "latest"), snapshot.homeSections.map { it.id })
        assertEquals(listOf("v1", "v2"), snapshot.homeSections[1].itemIds)
        assertEquals("UC1", snapshot.videos.first().channelId)
    }

    @Test
    fun flatCatalogDoesNotPretendToBeYouTubeHomeLayout() {
        val snapshot = YouTubeChannelResolver.resolve(
            """{
              "channel":{"channelId":"UC1","name":"Demo"},
              "videos":[{"videoId":"v1","title":"One","url":"https://www.youtube.com/watch?v=v1"}]
            }"""
        )
        assertFalse(snapshot.hasAuthoritativeHomeLayout)
        assertEquals(1, snapshot.videos.size)
    }
}
