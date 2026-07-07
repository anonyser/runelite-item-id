package com.anonyser.itemid;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RecipesTest
{
	@Test
	public void loadsTheBundledRecipesFile()
	{
		final Recipes recipes = Recipes.load();
		assertTrue(recipes.size() >= 10);
	}

	@Test
	public void resolvesResultAndComponentsToIds()
	{
		final Recipes recipes = new Recipes();
		recipes.add("Volatile nightmare staff|Nightmare staff|Volatile orb");

		final Map<String, Integer> names = new HashMap<>();
		names.put("volatile nightmare staff", 24422);
		names.put("nightmare staff", 24422 - 1);
		names.put("volatile orb", 24514);

		final Map<Integer, int[]> resolved = recipes.resolve(names);
		assertArrayEquals(new int[]{24421, 24514}, resolved.get(24422));
	}

	@Test
	public void dropsRecipesWithAnyUnresolvedName()
	{
		final Recipes recipes = new Recipes();
		recipes.add("Made thing|Known part|Unknown part");

		final Map<String, Integer> names = new HashMap<>();
		names.put("made thing", 100);
		names.put("known part", 101);
		// "unknown part" is absent

		assertFalse(recipes.resolve(names).containsKey(100));
	}

	@Test
	public void ignoresCommentsBlanksAndNonRecipeLines()
	{
		final Recipes recipes = new Recipes();
		recipes.add("# comment");
		recipes.add("   ");
		recipes.add("No pipe here");
		recipes.add("Result|Part");
		assertTrue(recipes.size() == 1);
	}
}
