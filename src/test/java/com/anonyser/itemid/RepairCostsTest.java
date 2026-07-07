package com.anonyser.itemid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RepairCostsTest
{
	@Test
	public void loadsTheBundledCsvAndMatchesByNameCaseInsensitively()
	{
		final RepairCosts costs = RepairCosts.load();
		assertTrue(costs.size() > 40);
		assertEquals(Integer.valueOf(150000), costs.cost("Fighter torso"));
		assertEquals(Integer.valueOf(150000), costs.cost("fighter torso"));
		assertEquals(Integer.valueOf(240000), costs.cost("Dragon defender"));
	}

	@Test
	public void unknownOrNullItemsHaveNoCost()
	{
		final RepairCosts costs = RepairCosts.load();
		assertNull(costs.cost("Dragon dagger"));
		assertNull(costs.cost(null));
	}

	@Test
	public void skipsCommentsBlanksAndMalformedRows()
	{
		final RepairCosts costs = new RepairCosts();
		costs.add("# a comment");
		costs.add("   ");
		costs.add("No number here");
		costs.add("Bad cost,abc");
		costs.add("Good item,1234");
		assertEquals(1, costs.size());
		assertEquals(Integer.valueOf(1234), costs.cost("Good item"));
	}
}
