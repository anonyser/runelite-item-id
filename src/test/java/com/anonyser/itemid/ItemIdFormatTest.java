package com.anonyser.itemid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ItemIdFormatTest
{
	@Test
	public void appendsIdInParenthesesAfterTheName()
	{
		assertEquals("<col=ff9040>Dragon dagger<col=ffffff> (1215)",
			ItemIdPlugin.withId("<col=ff9040>Dragon dagger", 1215));
		assertEquals("Lobster<col=ffffff> (379)",
			ItemIdPlugin.withId("Lobster", 379));
	}

	@Test
	public void leavesEmptyOrUnknownTargetsUntouched()
	{
		assertEquals("", ItemIdPlugin.withId("", 1215));
		assertNull(ItemIdPlugin.withId(null, 1215));
		assertEquals("Lobster", ItemIdPlugin.withId("Lobster", 0));
		assertEquals("Lobster", ItemIdPlugin.withId("Lobster", -1));
	}
}
