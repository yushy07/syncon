package com.yu.syncon.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMapperTest {

    @Test
    fun `maps standard social media apps correctly`() {
        assertEquals("Social Media", CategoryMapper.getDefaultCategory("com.instagram.android"))
        assertEquals("Social Media", CategoryMapper.getDefaultCategory("com.facebook.katana"))
        assertEquals("Social Media", CategoryMapper.getDefaultCategory("com.twitter.android"))
    }

    @Test
    fun `maps entertainment and browser apps correctly`() {
        assertEquals("Entertainment", CategoryMapper.getDefaultCategory("com.google.android.youtube"))
        assertEquals("Entertainment", CategoryMapper.getDefaultCategory("com.netflix.mediaclient"))
        assertEquals("Browser", CategoryMapper.getDefaultCategory("com.android.chrome"))
    }

    @Test
    fun `falls back to Other for unrecognized package names`() {
        assertEquals("Other", CategoryMapper.getDefaultCategory("com.example.unrecognized.app"))
    }
}
