package com.anonyser.itemid;

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WikiDescriptionTest
{
	private final Gson gson = new Gson();

	@Test
	public void parsesTheExtractFromAMediaWikiResponse()
	{
		final String json = "{\"query\":{\"pages\":{\"12345\":{\"pageid\":12345,"
			+ "\"title\":\"Dragon dagger\",\"extract\":\"The dragon dagger is a dagger. \"}}}}";
		assertEquals("The dragon dagger is a dagger.", WikiDescription.parse(gson, json));
	}

	@Test
	public void returnsNullForMissingPagesOrJunk()
	{
		assertNull(WikiDescription.parse(gson, "{\"query\":{\"pages\":{\"-1\":{\"missing\":\"\"}}}}"));
		assertNull(WikiDescription.parse(gson, "{\"query\":{\"pages\":{\"1\":{\"extract\":\"\"}}}}"));
		assertNull(WikiDescription.parse(gson, "not json at all"));
		assertNull(WikiDescription.parse(gson, "{}"));
	}

	@Test
	public void buildsAWikiApiUrlWithTheItemNameAsTitle()
	{
		final String url = WikiDescription.url("Volatile nightmare staff").toString();
		assertTrue(url.startsWith("https://oldschool.runescape.wiki/api.php"));
		assertTrue(url.contains("prop=extracts"));
		assertTrue(url.contains("titles=Volatile%20nightmare%20staff")
			|| url.contains("titles=Volatile+nightmare+staff"));
	}
}
